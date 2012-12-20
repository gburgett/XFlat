XFlat
=====

A lightweight XML persistence framework presenting a CRUD interface to flat XML files.

----

So you've got a Java application, and need to persist some data.  Cool.  Just write it to a file.

> Well, my data's in a bunch of POJOs.

Ok that's easy, just serialize it to XML and drop it into a DOM, then write it to a file.

> Alright, that sounds easy enough.  But wait a second, I need random access to my saved POJOs.

Well thats a bit harder, but you could write a class to inspect the DOM and pull out the data you need.

> But I also need to query my POJOs by arbitrary criteria.

Looks like you need a database.  Take a look at Derby or Sql Lite

> Well I don't really have that much data, and it's not very relational.  
> It would be really nice to just keep it in a flat file.

Oh, well in that case XFlat is for you!

---

XFlat is a single lightweight JAR that persists XML DOM Elements to flat files.
It presents as a CRUD interface to XML Elements that can be queried by ID or arbitrary XPath expressions.
Because it is stored in flat files, XFlat is not relational and is schemaless.
XFlat is effective for table sizes up to the dozens of KB.  Future versions may contain engines that are
effective up to the hundreds of MB, using memory-mapped files.

XFlat also comes with a converting wrapper that maps POJOs to DOM Elements using JAXB.  The underlying implementation
can be swapped if necessary.  The JAXB context is only loaded if it is used, so you can avoid it completely by using
only the JDOM CRUD interface.

XFlat manages a "database" as a directory of flat files.  Each "database" contains a number of "tables".
One table corresponds to one file, unless the table is sharded (to be implemented in future versions).
A table contains any number of "rows".  A row is simply an XML Dom Element in the XFlat namespace which contains
one child element.  This child element is the content of the row.

Rows can be queried by unique ID, or by an XPath expression combined with a Hamcrest matcher.  The unique ID
is always a string and is stored as an attribute on the row element.  The XPath query can select any DOM element
within a row, and the value of that DOM element is matched by the Hamcrest matcher to determine whether the row
is selected by the query.

The database manages each table using one of several "engines".  The engines are optimized for different file sizes,
and they can (and will) be hot-swapped as tables grow and shrink.  The simplest engine (and the only one implemented
for version 1.0) keeps the entire contents of the table in memory as a JDom Document, and flushes it to disk often.
This is obviously not workable for table sizes greater than dozens of kb, so future versions will contain engines
for larger sizes.

(not implemented for version 1.0)
XFlat can also index tables by arbitrary xpath expressions.  The values of the content selected by the expression 
are kept in the index for optimizing future queries.  XPath Queries that use any non-indexed expressions will 
cause a full table scan.

(not impelemented for version 1.0)
XFlat has the capability to "shard" tables.  Sharding is splitting the table's contents into multiple files
by some indexed value (most often ID).  Queries on that index then know which file to open in order to get at the
correct data.  This is only useful when tables grow very large.

====

Required dependencies:

* JDOM 2
  * jdom-2.0.4.jar
  
* Hamcrest matchers 1.3
  * hamcrest-core-1.3.jar
  * hamcrest-library-1.3.jar 
   
* Apache Commons Logging 1.1
  * commons-logging-1.1.1.jar

Optional dependencies:

* Jaxen-1.1.4 - for compiling XPath strings into expressions
  * jaxen-1.1.4.jar
  
* JAXB reference implementation 1.0 - for automatic POJO mapping
