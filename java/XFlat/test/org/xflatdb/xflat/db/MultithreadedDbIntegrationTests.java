/* 
*	Copyright 2013 Gordon Burgett and individual contributors
*
*	Licensed under the Apache License, Version 2.0 (the "License");
*	you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*	Unless required by applicable law or agreed to in writing, software
*	distributed under the License is distributed on an "AS IS" BASIS,
*	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*	See the License for the specific language governing permissions and
*	limitations under the License.
*/
package org.xflatdb.xflat.db;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.AssertionFailedError;
import org.hamcrest.Matchers;
import org.jdom2.Element;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xflatdb.xflat.Table;
import test.Foo;
import test.Utils;
import static org.junit.Assert.*;
import org.xflatdb.xflat.query.XPathQuery;
import org.xflatdb.xflat.query.XPathUpdate;
import org.xflatdb.xflat.transaction.Propagation;
import org.xflatdb.xflat.transaction.TransactionOptions;
import org.xflatdb.xflat.transaction.TransactionScope;

/**
 *
 * @author Gordon
 */
public class MultithreadedDbIntegrationTests {
    
    static File workspace = new File("integrationtests");
    
    @BeforeClass
    public static void setUpClass(){
        if(workspace.exists()){
            Utils.deleteDir(workspace);
        }
    }
    
    private LocalTransactionalDatabase getDatabase(String testName){
        File dbDir = new File(workspace, testName);
        LocalTransactionalDatabase ret = new LocalTransactionalDatabase(dbDir);
        
        return ret;
    }
    
    private List<Thread> run(Runnable r, int threads){
        List<Thread> ret = new ArrayList<>();
        for(int i = 0; i < threads; i++){
            ret.add(new Thread(r));
        }
        
        for(int i = 0; i < threads; i++){
            ret.get(i).start();
        }
        return ret;
    }
    
    private void spinWait(long nanos){
        long start = System.nanoTime();
        long diff = 0;
        do{
            diff = System.nanoTime() - start;
            if((diff & 0xFFFF) == 0L) //approx. 65 uS
                Thread.yield();
            
        }while(diff - nanos < 0);
    }
    
    private static final Random seedRandom = new Random();
    private ThreadLocal<Random> random = new ThreadLocal<Random>(){
        @Override
        protected Random initialValue(){
            return new Random(seedRandom.nextLong());
        }
    };
    
    @Test
    public void HeavyRead_OneUpdate_AfterInsertAllGetNewValue() throws Exception {
        System.out.println("HeavyRead_OneUpdate_AfterInsertAllGetNewValue");
        
        final AtomicBoolean finished = new AtomicBoolean(false);
        final LocalTransactionalDatabase db = getDatabase("HeavyRead_OneUpdate_AfterInsertAllGetNewValue");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.initialize();
        try{
            
        final Foo oldFoo = new Foo();
        oldFoo.fooInt = 5;
        oldFoo.setId("1");
        
        final Foo newFoo = new Foo();
        newFoo.fooInt = 6;
        newFoo.setId("1");
        
        final AtomicInteger counter = new AtomicInteger(0);
        
        final List<Integer> allOldFooCounters = new ArrayList<>();
        final List<Integer> allNewFooCounters = new ArrayList<>();
        
        //set up readers
        Runnable r = new Runnable(){
            @Override
            public void run() {
                List<Integer> oldFooCounters = new ArrayList<>();
                List<Integer> newFooCounters = new ArrayList<>();
                
                Table<Foo> fooTable = db.getTable(Foo.class);
                
                while(!finished.get()){
                    
                    Integer count = counter.incrementAndGet();
                    Foo foo = fooTable.find("1");
                    if(oldFoo.equals(foo))
                        oldFooCounters.add(count);
                    else if(newFoo.equals(foo))
                        newFooCounters.add(count);
                }
                
                synchronized(allOldFooCounters){
                    allOldFooCounters.addAll(oldFooCounters);
                }
                synchronized(allNewFooCounters){
                    allNewFooCounters.addAll(newFooCounters);
                }
            }
        };
        
        Table<Foo> fooTable = db.getTable(Foo.class);
        fooTable.insert(oldFoo);
        
        Integer before;
        Integer after;
        
        List<Thread> th = run(r, 3);

        Thread.sleep(10);

        //act
        before = counter.incrementAndGet();
        fooTable.replace(newFoo);
        after = counter.incrementAndGet();

        finished.set(true);   
        
        for(Thread t : th){
            t.join();
        }
        
        //ASSERT
        
        //there may be instances between "before" and "after" where we find both,
        //but all the instances of "OldFoo" should definitely be before "after"
        //and all instances of "NewFoo" should definitely be after "before".
        
        assertThat("All counters in oldFooCounters should be prior to 'after'", 
                allOldFooCounters, Matchers.everyItem(Matchers.lessThan(after)));
        
        //assert all new foo counters are post-after
        assertThat("All counters in newFooCounters should be post 'before'", 
                allNewFooCounters, Matchers.everyItem(Matchers.greaterThan(before)));
        
        }finally{
            finished.set(true);
            db.shutdown();
        }
    }
    
    @Test
    public void HeavyWrite_OneReader_AllReadsInOrder() throws Exception {
        System.out.println("HeavyWrite_OneReader_AllReadsInOrder");
        
        final AtomicBoolean finished = new AtomicBoolean(false);
        final LocalTransactionalDatabase db = getDatabase("HeavyWrite_OneReader_AllReadsInOrder");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.initialize();
        try{
            final AtomicInteger counter = new AtomicInteger(0);
            final AtomicInteger idStarter = new AtomicInteger(0);
        
            final AtomicInteger numInserts = new AtomicInteger(0);
            
            final AtomicReference<Exception> lastException = new AtomicReference<>(null);
            
            Runnable r = new Runnable(){
                @Override
                public void run() {
                    try{
                        String id = Integer.toString(idStarter.incrementAndGet());


                        Table<Foo> fooTable = db.getTable(Foo.class);

                        while(!finished.get()){

                            Foo foo = new Foo();
                            foo.fooInt = counter.incrementAndGet();
                            //put it in one of 3 ID slots
                            foo.setId(id);

                            boolean inserted = fooTable.upsert(foo);
                            if(inserted)
                                numInserts.incrementAndGet();
                        }

                        //do one more - this will be the final one
                        Foo foo = new Foo();
                        foo.fooInt = counter.incrementAndGet();
                        //put it in one of 4 ID slots
                        foo.setId(id);

                        boolean inserted = fooTable.upsert(foo);  
                        if(inserted)
                            numInserts.incrementAndGet();
                    
                    }catch(Exception ex){
                        System.err.println(ex.toString());
                        lastException.set(ex);
                    }
                }
            };
            
            Table<Foo> fooTable = db.getTable(Foo.class);
            List<Integer> foos = new ArrayList<>(4);
            Integer after, before;
            
            List<Thread> th = run(r, 3);
                
            Thread.sleep(10);
                
            
            before = counter.incrementAndGet();
            finished.set(true);
            for(Thread t : th){
                t.join();
            }
            
            
            //ASSERT
            if(lastException.get() != null){
                fail(lastException.get().getMessage());
            }
                        
            foos.add(fooTable.find("1").fooInt);
            foos.add(fooTable.find("2").fooInt);
            foos.add(fooTable.find("3").fooInt);
            after = counter.incrementAndGet();
                
            assertThat(foos, Matchers.everyItem(Matchers.lessThan(after)));
            assertThat(foos, Matchers.everyItem(Matchers.greaterThan(before)));
            
            assertEquals(3, numInserts.get());
        }
        finally{
            finished.set(true);
            db.shutdown();
        }
    }
    
    
    @Test
    public void testTransactionalWrites_InOwnThread_MaintainsIsolation() throws Exception {
        System.out.println("testTransactionalWrites_InOwnThread_MaintainsIsolation");
        
        final AtomicBoolean finished = new AtomicBoolean(false);
        final LocalTransactionalDatabase db = getDatabase("HeavyWrite_OneReader_AllReadsInOrder");
        
        db.getConversionService().addConverter(Foo.class, Element.class, new Foo.ToElementConverter());
        db.getConversionService().addConverter(Element.class, Foo.class, new Foo.FromElementConverter());
        
        db.initialize();
        try{
            final AtomicInteger counter = new AtomicInteger(0);
            final AtomicInteger idStarter = new AtomicInteger(0);
            final AtomicReference<Throwable> lastException = new AtomicReference<>(null);
            
            final XPathExpression<Object> fooInt = XPathFactory.instance().compile("foo/fooInt");                    
            
            Runnable r = new Runnable() {

                @Override
                public void run() {
                    int start = idStarter.incrementAndGet();
                    
                    try{
                        
                        Table<Foo> fooTable = db.getTable(Foo.class);
                        
                        Map<String, Foo> insertedFoos = new HashMap<>();
                        
                        //should be isolated
                        try(TransactionScope tx = db.getTransactionManager().openTransaction(TransactionOptions.DEFAULT.withPropagation(Propagation.REQUIRES_NEW))){

                            int count = start;
                            while((++count % 100) != start){
                                //go around the loop till we get back to start
                                
                                Foo foo = new Foo();
                                foo.fooInt = random.get().nextInt();
                                //drop it in the "count" ID.  We are definitely
                                //stepping on the toes of another thread here,
                                //but hopefully not at the same time to avoid synclock.
                                foo.setId(Integer.toString(count));
                                
                                //ought not to throw DuplicateKeyException
                                fooTable.insert(foo);
                                
                                insertedFoos.put(foo.getId(), foo);
                            }
                            
                            for(Map.Entry<String, Foo> foos : insertedFoos.entrySet()){
                                //we ought to be able to find the data in the DB
                                
                                Foo foo = foos.getValue();
                                
                                Foo inDb = fooTable.find(foos.getKey());
                                assertEquals("Unequal foos at ID " + foos.getKey(), foo, inDb);
                            }
                            
                            int rand = random.get().nextInt(insertedFoos.size());
                            int cnt = 0;
                            Foo foo = null;
                            for(Foo f : insertedFoos.values()){
                                if(cnt++ == rand){
                                    foo = f;
                                    break;
                                }                                
                            }
                            
                            int newFooInt = random.get().nextInt();
                            fooTable.update(XPathQuery.eq(fooInt, foo.fooInt), XPathUpdate.set(fooInt, newFooInt));
                            foo = fooTable.find(foo.getId());
                            assertEquals("Should update in TX", newFooInt, foo.fooInt);
                        }
                    }catch(Throwable ex){
                        synchronized(this){
                            System.err.println("Error in thread with start " + start);
                            System.err.println(ex.toString());
                            System.err.println(ex.getStackTrace());
                        }
                        lastException.set(ex);
                    }
                }
            };
            
            //ACT
            Table<Foo> fooTable = db.getTable(Foo.class);
            List<Foo> inMainThread = new ArrayList<>();
            
            List<Thread> threads = run(r, 3);
            
            
            //should see nothing in DB while these transactions are running
            inMainThread.add(fooTable.find("1"));
            inMainThread.add(fooTable.find("2"));
            inMainThread.add(fooTable.find("3"));
            inMainThread.add(fooTable.find("4"));
            
            Foo foo = new Foo();
            foo.fooInt = 7;
            fooTable.insert(foo);
            
            Foo inDb = fooTable.find(foo.getId());
            
            for(Thread t : threads)
                t.join();
            
            //ASSERT
            if(lastException.get() != null){
                throw new Exception("Failure while processing", lastException.get());
            }
            
            assertThat(inMainThread, Matchers.everyItem(Matchers.nullValue(Foo.class)));
            assertEquals("Should have retrieved non-transactional data", foo, inDb);
            assertEquals("Should have retrieved non-transactional data", foo, fooTable.find(foo.getId()));
            
        }finally{
            finished.set(true);
            db.shutdown();
        }
    }
}
