/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

import org.xflatdb.xflat.db.XFlatDatabase;
import org.xflatdb.xflat.db.EngineTransactionManager;
import org.xflatdb.xflat.db.EngineBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.xflatdb.xflat.transaction.Transaction;
import org.xflatdb.xflat.transaction.TransactionException;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 * @author Gordon
 */
public abstract class EngineTransactionManagerTestBase {
    
    public EngineTransactionManagerTestBase() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    

    protected abstract EngineTransactionManager getInstance();

    @Test
    public void testBeginTransaction_TransactionIsOpen() throws Exception {
        System.out.println("testBeginTransaction_TransactionIsOpen");
     
        try(EngineTransactionManager instance = getInstance())
        {
        
            Transaction tx = instance.openTransaction();

            assertFalse("TX should not be committed", tx.isCommitted());
            assertEquals("TX should not be committed", -1, instance.isTransactionCommitted(tx.getTransactionId()));
            assertFalse("TX should not be reverted", tx.isReverted());
            assertFalse("TX should not be reverted", instance.isTransactionReverted(tx.getTransactionId()));
            assertTrue("Should be one open TX", instance.anyOpenTransactions());
            assertEquals(tx.getTransactionId(), instance.getLowestOpenTransaction());



            tx.commit();

            assertTrue("TX should be committed", tx.isCommitted());
            assertEquals("TX should be committed", tx.getCommitId(), instance.isTransactionCommitted(tx.getTransactionId()));
            assertFalse("TX should not be reverted", tx.isReverted());
            assertFalse("TX should not be reverted", instance.isTransactionReverted(tx.getTransactionId()));
            assertThat("TX should have higher commit ID", tx.getCommitId(), Matchers.greaterThan(tx.getTransactionId()));

            tx.close();

            assertFalse("Should be no open TX", instance.anyOpenTransactions());
            assertEquals(Long.MAX_VALUE, instance.getLowestOpenTransaction());

        }
    }
    
    @Test
    public void testRevertTransaction_TransactionIsReverted() throws Exception {
        System.out.println("testRevertTransaction_TransactionIsReverted");
        
        try(EngineTransactionManager instance = getInstance())
        {
        
            Transaction tx = instance.openTransaction();

            tx.revert();

            assertFalse("TX should not be committed", tx.isCommitted());
            assertEquals("TX should not be committed", -1, instance.isTransactionCommitted(tx.getTransactionId()));
            assertTrue("TX should be reverted", tx.isReverted());
            assertTrue("TX should be reverted", instance.isTransactionReverted(tx.getTransactionId()));
            assertEquals("TX should have no commit ID", -1, tx.getCommitId());

            tx.close();

            assertFalse("Should be no open TX", instance.anyOpenTransactions());
            assertEquals(Long.MAX_VALUE, instance.getLowestOpenTransaction());
        }
    }
    
    @Test
    public void testGetTransaction_GetsCurrentTransaction() throws Exception {
        System.out.println("testGetTransaction_GetsCurrentTransaction");
        
        try(EngineTransactionManager instance = getInstance())
        {
        
            Transaction tx = instance.openTransaction();

            Transaction current = instance.getTransaction();

            assertEquals("Should be same transaction", tx.getTransactionId(), current.getTransactionId());

            tx.close();

            assertNull("getTransaction should be null after closing", instance.getTransaction());

        }
        
    }
    
    @Test
    public void testTransactionlessCommitId_BeforeAndAfterTx_LTAndGTTxId() throws Exception {
        System.out.println("testTransactionlessCommitId_BeforeAndAfterTx_LTAndGTTxId");
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            long id1 = instance.transactionlessCommitId();

            Transaction tx = instance.openTransaction();

            long id2 = instance.transactionlessCommitId();

            tx.close();

            assertThat(id1, Matchers.lessThan(tx.getTransactionId()));
            assertThat(id2, Matchers.greaterThan(tx.getTransactionId()));
        }
    }
    
    @Test
    public void testTransactionIDs_HeavyUse_ThreadSafe() throws Exception {
        System.out.println("testTransactionIDs_HeavyUse_ThreadSafe");
        
        try(final EngineTransactionManager instance = getInstance())
        {
            final Set<List<Long>> ids = Collections.synchronizedSet(new HashSet<List<Long>>());

            Runnable r = new Runnable(){
                @Override
                public void run() {
                    List<Long> idList = new ArrayList<>(500);

                    for(int i = 0; i < 500; i++){
                        idList.add(instance.transactionlessCommitId());
                    }

                    ids.add(idList);
                }
            };

            Thread th1 = new Thread(r);
            Thread th2 = new Thread(r);
            Thread th3 = new Thread(r);

            long start = instance.transactionlessCommitId();

            th1.start();
            th2.start();
            th3.start();
            r.run();

            th1.join();
            th2.join();
            th3.join();

            long end = instance.transactionlessCommitId();


            int maxUniquifier = 0;

            Set<Long> finalIds = new HashSet<>();
            for(List<Long> idList : ids){
                for(Long l : idList){
                    assertTrue("Duplicate IDs generated", finalIds.add(l));
                    assertThat("id not greater than start", l, Matchers.greaterThan(start));
                    assertThat("id not less than end", l, Matchers.lessThan(end));

                    int i = (int)(l.longValue() & 0xFFFFL);
                    maxUniquifier = i > maxUniquifier ? i : maxUniquifier;
                }
            }

            System.out.println("Max uniquifier: " + maxUniquifier);
        }
    }

    @Test
    public void testBindEngine_BoundEngineNotifiedOfRevert() throws Exception {
        System.out.println("testBindEngine_BoundEngineNotifiedOfRevert");
        
        try(EngineTransactionManager instance = getInstance())
        {
            Transaction tx = instance.openTransaction();

            EngineBase e = mock(EngineBase.class);

            instance.bindEngineToCurrentTransaction(e);

            tx.revert();

            verify(e).revert(tx.getTransactionId(), false);

            tx.close();
        }
    }
    
    @Test
    public void testBindEngine_BoundEngineNotifiedOfCommit() throws Exception {
        System.out.println("testBindEngine_BoundEngineNotifiedOfCommit");
        
        try(EngineTransactionManager instance = getInstance())
        {
               
            Transaction tx = instance.openTransaction();

            EngineBase e = mock(EngineBase.class);

            instance.bindEngineToCurrentTransaction(e);

            tx.commit();

            verify(e).commit(tx);
            
            tx.close();
        }
    }
    
    @Test
    public void testBindEngine_ExceptionDuringCommit_BoundEngineReverted() throws Exception {
        System.out.println("testBindEngine_ExceptionDuringCommit_BoundEngineReverted");
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            Transaction tx = instance.openTransaction();

            EngineBase e = mock(EngineBase.class);
            doThrow(new TransactionException("Test"){})
                .when(e).commit(any(Transaction.class));


            instance.bindEngineToCurrentTransaction(e);

            try{
                tx.commit();
                fail("Did not throw TransactionException");
            }
            catch(TransactionException ex){
                //expected
            }

            verify(e).revert(tx.getTransactionId(), false);
            
            tx.close();
        }
    }
    
    @Test
    public void testMultipleBoundEngines_SecondEngineThrows_FirstBoundEngineRevertedAfterCommit() throws Exception {
        System.out.println("testMultipleBoundEngines_SecondEngineThrows_FirstBoundEngineRevertedAfterCommit");
        
        try(EngineTransactionManager instance = getInstance())
        {
            Transaction tx = instance.openTransaction();

            final AtomicReference<EngineBase> committedEngine = new AtomicReference<>(null);
            
            Answer a = new Answer(){
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    EngineBase eng = (EngineBase)invocation.getMock();
                    if(!committedEngine.compareAndSet(null, eng)){
                        //the second one should throw
                        throw new TransactionException("Test"){};
                    }
                    return null;
                }
            };
            
            EngineBase e = mock(EngineBase.class);
            EngineBase e2 = mock(EngineBase.class);
            doAnswer(a)
                .when(e).commit(any(Transaction.class));
            doAnswer(a)
                .when(e2).commit(any(Transaction.class));

            instance.bindEngineToCurrentTransaction(e);
            instance.bindEngineToCurrentTransaction(e2);

            try{
                tx.commit();
                fail("Did not throw TransactionException");
            }
            catch(TransactionException ex){
                //expected
            }

            verify(e).revert(tx.getTransactionId(), false);
            verify(e2).revert(tx.getTransactionId(), false);
            
            verify(committedEngine.get()).commit(tx);
            
            tx.close();
            
        }
    }
    
    @Test
    public void testBoundEngineFails_InstanceClosed_Recovers() throws Exception {
        System.out.println("testBoundEngineFails_InstanceClosed_Recovers");
        
        long txId;
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            Transaction tx = instance.openTransaction();
            txId = tx.getTransactionId();

            EngineBase e = mock(EngineBase.class);
            doThrow(new TransactionException("Test"){})
                .when(e).commit(any(Transaction.class));
            doThrow(new Error("Expected"))
                .when(e).revert(any(Long.class), anyBoolean());
            doReturn("Name")
                .when(e).getTableName();
            
            instance.bindEngineToCurrentTransaction(e);

            try{
                tx.commit();
                fail("Did not throw TransactionException");
            }
            catch(Error err){
                if(!"Expected".equals(err.getMessage()))
                    throw err;
                
                //expected
            }
            
            tx.close();
        }
        
        EngineBase e = mock(EngineBase.class);
        
        XFlatDatabase db = mock(XFlatDatabase.class);
        when(db.getEngine("Name"))
                .thenReturn(e);
        
        try(EngineTransactionManager instance = getInstance())
        {
            instance.recover(db);
        }
        
        //verify recovered
        verify(e).revert(txId, true);
    }
    
}
