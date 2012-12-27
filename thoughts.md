

===
### Transitions

Xflat needs to have hot-swappable engines.  That is one of the key features, so we can keep a lightweight engine for small file sizes but move up to heavyweight engines for larger file sizes.  We need to define the transition from one engine to another.
One of the major issues is that the engine can take a long time to fire up, especially if it has to build indexes or even rewrite the file to a new format.  We need to make sure we're read-responsive throughout the transition, and hopefully stay write-responsive as much as possible.
A requirement of consistency is that in the normal use case of a single thread, if it writes data then all future reads from that thread MUST be able to read that data.  This makes it even more difficult.  We have a couple options to maintain write consistency: we can block the writing thread until the transition is complete, or replicate and cache writes to ensure that the new engine has the appropriate data when it is ready.

engine lifecycle:
1. pre-initialized
  * the state when the object is newly constructed.  At this point it is not expected to respond to requests and may throw exceptions if requests are made.
2. spinning up
  * at this point the engine should have read-access to the file and can begin loading it into memory or building indexes or whatever.  It does not yet need to respond to read requests and can throw exceptions or return inconsistent data.  It must accept write requests, and depending on the engine it may block the writing thread or cache the write requests to maintain data integrity upon handover.
3. spun up
  * at this point the engine has finished indexing/rewriting data.  It returns correct data from reads and is ready for the handover, but does not yet have write access to the underlying file if it is using the same file as the previous engine.  If it was rewriting the data it should have been rewriting to a temporary file that it has write access to, and can continue writing to that.
4. running
  * At this point the engine has full access to the underlying file.  If it had rewritten the file into a new format, it deletes the old file and moves the new file to the old file's name.  It is responding to read and write requests normally.
5. spinning down
  * At this point the engine stops responding to write requests, and should throw an exception if write requests are received.  It finishes up any unfinished persisting of data to the file and waits for the user to close remaining outstanding cursors.  It should still respond to read requests but the database should ensure it does not receive any.  It still has read-access to the underlying file, primarily for use by still outstanding cursors.
6. spun down
  * The engine should release any access to the underlying file.  It should throw exceptions if any read or write requests are made.  At this point it has completely handed off to a new engine.  It should not be expected to revert back to pre-initialized, the Database will create a new engine instance if it needs to go back through the lifecycle.  It should be able to be collected by the garbage collector.
  
All this necessitates a Transitioning Engine class that manages an engine transition.  The transition should occur in the following way:
1. constructed
  * It is constructed with a reference to the current engine and the new engine.  It should pass all requests to the current engine.
2. swapped in
  * the database swaps the old engine with the Transitioning Engine in all the tables, so the tables all call into the Transitioning Engine.
3. begin transition
  * It should obtain its write lock and then call `spinUp()` on the new engine.  All writes should block until `spinUp()` returns, at which point the new engine should be in the "spinning up" state.  The TransitioningEngine should then duplicate writes to both engines, but direct reads only to the original engine.
4. new engine spun up
  * The new engine notifies the transitioning engine by event that it has finished spinning up and is in the "spun up" state.  The TransitioningEngine then redirects both reads and writes to the new engine and calls `spinDown()` on the old engine.
5. old engine spun down
  * The old engine notifies the transitioning engine by event that it has finished spinning down and is in the "spun down" state.  At this point the transitioning engine calls `beginOperations()` on the new engine to transition it to the "running" state.  It then notifies the database that the transition is complete and the database swaps out the transitioning engine with the new engine.
 