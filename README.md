XFlat
=====

A lightweight embedded no-sql object DB persisting objects to flat XML files.
XFlat is a completely free alternative to db4o for an embedded object DB.

----

So you've got a Java application, and need to persist some data.  Cool.  Just write it to a file.

> Well, my data's in a bunch of POJOs.

Ok that's easy, just serialize it to XML and drop it into a DOM, then write it to a file.

> Alright, that sounds easy enough.  But wait a second, I need random access to my saved POJOs.

Well thats a bit harder, but you could write a class to inspect the DOM and pull out the data you need.

> But I also need to query my POJOs by arbitrary criteria.

Looks like you need a database.  Take a look at db4o or Sql Lite

> Well I'd prefer not to have to deal with Sql, and I may need to inspect or transform my data as XML.

Oh, well in that case XFlat is for you!

---

XFlat is a single lightweight JAR that persists XML DOM Elements to flat files.
It presents as a CRUD interface to XML Elements that can be queried by ID or arbitrary XPath expressions.
Because it is stored in flat files, XFlat is not relational and is schemaless.
XFlat is currently effective for table sizes up to the dozens of KB.  Future versions will contain engines that are
effective up to the hundreds of MB, using memory-mapped files.


#### Features:
* Pure XML data files
  * XFlat's data files are pure XML.  This means they can be inspected, queried, transformed and manipulated by common
XML tools like XSLT and XQuery.  It is trivial to create a process to export your entire database, or import data
from another process into your database by directly manipulating the XML (provided the database is not running during
the import, which is simple to control).  XFlat will automatically re-index the data files on startup if they have
changed.


* POJO mapping to XML
  * XFlat maps POJOs to JDOM `Element` objects using JAXB.  The underlying implementation can be swapped if necessary.
The JAXB context is only loaded if it is used, so you can avoid it completely by specifying custom converters,
or using only the JDOM CRUD interface.


* Queriable by XPath expressions
  * Tables can be queried by any arbitrary XPath expression using the table row as the context.  The expression
selects a part of the `Element` that is convertible to the value which is being matched, then the matching is performed
using Hamcrest Matchers.  Breaking the query into XPath expressions and values allows the engine to leverage indexes
effectively, much more easily than if we used XQuery.  Future versions may support XQuery.


* Multiple hot-swapped Engines (to be implemented)
  * The management of each table is handled by an Engine.  As a table grows or shrinks, the appropriate Engine for managing
the data is swapped in behind the scenes.  For example, very small tables can use an engine that loads the whole
XML DOM in-memory, while very large tables can use an engine that manipulates a memory-mapped file.



* Indexing on XPath expressions (to be implemented)
  * Engines can take advantage of indexes that are based on any arbitrary XPath expression.  The expression selects a
part of the `Element` that is converted to a `Comparable` (such as an Integer), then the engine can map that `Comparable`
to the row and binary search indexes to improve performance.



* Sharding on XPath expressions (to be implemented)
  * A table can be sharded across multiple files based on a sharding key selected by an XPath expression.  The expression
selects a part of the `Element` that is converted to a `Comparable`, then a `RangeProvider` determines which file to store
the Element in.


* ACID Transactions (to be implemented)
  * XFlat can support Transactions which can be saved and resumed.  All uncommitted transactional data is persisted in
the same file as the table data, so transactions can be resumed between program execution.

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

====
### Examples

Insert an instance of `Foo` into the table "Foo" stored in "myDataDirectory/Foo.xml".
```java
XFlatDatabase db = new XFlatDatabase(new File("myDataDirectory"));
//initialize with default config
db.initialize();

Foo myFoo = new Foo();
Table<Foo> fooTable = db.getTable(Foo.class);
fooTable.insert(myFoo);  //inserts with unique automatically-generated ID
System.out.println("Stored foo in table Foo with ID " + myFoo.getId());

Foo myFoo2 = fooTable.find(myFoo.getId());
//myFoo2 is a new instance with the same data as myFoo
```

See "Examples.md" for more examples
