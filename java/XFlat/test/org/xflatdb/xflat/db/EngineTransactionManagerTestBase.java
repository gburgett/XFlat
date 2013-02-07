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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.xflatdb.xflat.transaction.Transaction;
import org.xflatdb.xflat.transaction.TransactionException;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xflatdb.xflat.transaction.IllegalTransactionStateException;
import org.xflatdb.xflat.transaction.Propagation;
import org.xflatdb.xflat.transaction.TransactionOptions;
import org.xflatdb.xflat.transaction.TransactionPropagationException;
import org.xflatdb.xflat.transaction.TransactionScope;

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
        
            TransactionScope scope = instance.openTransaction();
            Transaction txTransaction = instance.getTransaction();
            

            assertFalse("TX should not be committed", scope.isCommitted());
            assertEquals("TX should not be committed", -1, instance.isTransactionCommitted(txTransaction.getTransactionId()));
            assertFalse("TX should not be reverted", scope.isReverted());
            assertFalse("TX should not be reverted", instance.isTransactionReverted(txTransaction.getTransactionId()));
            assertTrue("Should be one open TX", instance.anyOpenTransactions());
            assertEquals(txTransaction.getTransactionId(), instance.getLowestOpenTransaction());



            scope.commit();

            assertTrue("TX should be committed", scope.isCommitted());
            assertEquals("TX should be committed", txTransaction.getCommitId(), instance.isTransactionCommitted(txTransaction.getTransactionId()));
            assertFalse("TX should not be reverted", scope.isReverted());
            assertFalse("TX should not be reverted", instance.isTransactionReverted(txTransaction.getTransactionId()));
            assertThat("TX should have higher commit ID", txTransaction.getCommitId(), Matchers.greaterThan(txTransaction.getTransactionId()));

            scope.close();

            assertFalse("Should be no open TX", instance.anyOpenTransactions());
            assertEquals(Long.MAX_VALUE, instance.getLowestOpenTransaction());

        }
    }
    
    @Test
    public void testRevertTransaction_TransactionIsReverted() throws Exception {
        System.out.println("testRevertTransaction_TransactionIsReverted");
        
        try(EngineTransactionManager instance = getInstance())
        {
        
            TransactionScope scope = instance.openTransaction();
            Transaction txTransaction = instance.getTransaction();
            

            scope.revert();

            assertFalse("TX should not be committed", scope.isCommitted());
            assertEquals("TX should not be committed", -1, instance.isTransactionCommitted(txTransaction.getTransactionId()));
            assertTrue("TX should be reverted", scope.isReverted());
            assertTrue("TX should be reverted", instance.isTransactionReverted(txTransaction.getTransactionId()));
            assertEquals("TX should have no commit ID", -1, txTransaction.getCommitId());

            scope.close();

            assertFalse("Should be no open TX", instance.anyOpenTransactions());
            assertEquals(Long.MAX_VALUE, instance.getLowestOpenTransaction());
        }
    }
    
    @Test
    public void testGetTransaction_GetsCurrentTransaction() throws Exception {
        System.out.println("testGetTransaction_GetsCurrentTransaction");
        
        try(EngineTransactionManager instance = getInstance())
        {
        
            TransactionScope scope = instance.openTransaction();
            
            Transaction current = instance.getTransaction();

            assertNotNull("should have current transaction", current);
            assertThat("current should have an ID", current.getTransactionId(), Matchers.not(Matchers.equalTo(-1L)));

            scope.close();

            assertNull("getTransaction should be null after closing", instance.getTransaction());

        }
        
    }
    
    @Test
    public void testTransactionlessCommitId_BeforeAndAfterTx_LTAndGTTxId() throws Exception {
        System.out.println("testTransactionlessCommitId_BeforeAndAfterTx_LTAndGTTxId");
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            long id1 = instance.transactionlessCommitId();

            TransactionScope scope = instance.openTransaction();
            Transaction tx = instance.getTransaction();
            
            long id2 = instance.transactionlessCommitId();

            scope.close();

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
            TransactionScope scope = instance.openTransaction();

            EngineBase e = mock(EngineBase.class);

            instance.bindEngineToCurrentTransaction(e);

            scope.revert();

            verify(e).revert(instance.getTransaction().getTransactionId(), false);

            scope.close();
        }
    }
    
    @Test
    public void testBindEngine_BoundEngineNotifiedOfCommit() throws Exception {
        System.out.println("testBindEngine_BoundEngineNotifiedOfCommit");
        
        try(EngineTransactionManager instance = getInstance())
        {
               
            TransactionScope scope = instance.openTransaction();
            Transaction tx = instance.getTransaction();

            EngineBase e = mock(EngineBase.class);

            instance.bindEngineToCurrentTransaction(e);

            scope.commit();

            verify(e).commit(argThat(matchesTransaction(tx)), argThat(Matchers.equalTo(TransactionOptions.DEFAULT)));
            
            scope.close();
        }
    }
    
    @Test
    public void testBindEngine_ExceptionDuringCommit_BoundEngineReverted() throws Exception {
        System.out.println("testBindEngine_ExceptionDuringCommit_BoundEngineReverted");
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            TransactionScope scope = instance.openTransaction();
            Transaction tx = instance.getTransaction();

            EngineBase e = mock(EngineBase.class);
            doThrow(new TransactionException("Test"){})
                .when(e).commit(any(Transaction.class), any(TransactionOptions.class));


            instance.bindEngineToCurrentTransaction(e);

            try{
                scope.commit();
                fail("Did not throw TransactionException");
            }
            catch(TransactionException ex){
                //expected
            }

            verify(e).revert(tx.getTransactionId(), false);
            
            scope.close();
        }
    }
    
    @Test
    public void testMultipleBoundEngines_SecondEngineThrows_FirstBoundEngineRevertedAfterCommit() throws Exception {
        System.out.println("testMultipleBoundEngines_SecondEngineThrows_FirstBoundEngineRevertedAfterCommit");
        
        try(EngineTransactionManager instance = getInstance())
        {
            TransactionScope scope = instance.openTransaction();
            Transaction tx = instance.getTransaction();

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
                .when(e).commit(any(Transaction.class), any(TransactionOptions.class));
            doAnswer(a)
                .when(e2).commit(any(Transaction.class), any(TransactionOptions.class));

            instance.bindEngineToCurrentTransaction(e);
            instance.bindEngineToCurrentTransaction(e2);

            try{
                scope.commit();
                fail("Did not throw TransactionException");
            }
            catch(TransactionException ex){
                //expected
            }

            verify(e).revert(tx.getTransactionId(), false);
            verify(e2).revert(tx.getTransactionId(), false);
            
            verify(committedEngine.get()).commit(tx, TransactionOptions.DEFAULT);
            
            scope.close();
            
        }
    }
    
    @Test
    public void testBoundEngineFails_InstanceClosed_Recovers() throws Exception {
        System.out.println("testBoundEngineFails_InstanceClosed_Recovers");
        
        long txId;
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            TransactionScope tx = instance.openTransaction();
            Transaction txTransaction = instance.getTransaction();
            
            txId = txTransaction.getTransactionId();

            EngineBase e = mock(EngineBase.class);
            doThrow(new TransactionException("Test"){})
                .when(e).commit(any(Transaction.class), any(TransactionOptions.class));
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
    
    //<editor-fold desc="propagation">
    
    @Test
    public void testMandatoryPropagation_NoTransaction_ThrowsException() throws Exception {
        System.out.println("testMandatoryPropagation_NoTransaction_ThrowsException");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try{
                
                instance.openTransaction(new TransactionOptions().withPropagation(Propagation.MANDATORY));
                
                fail("should have thrown TransactionPropagationException");
            }catch(TransactionPropagationException ex){
            //expected
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testMandatoryPropagation_TransactionExists_AmbientTransactionRequiresBothCommits() throws Exception {
        System.out.println("testMandatoryPropagation_TransactionExists_AmbientTransactionRequiresBothCommits");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                
                 //act
                TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.MANDATORY));
                
                
                inner.commit();
                
                assertFalse("Ambient transaction should not yet be committed", inner.isCommitted());
                
                inner.close();
                
                //assert
                assertFalse("Ambient transaction should not yet be committed", outer.isCommitted());
                
                outer.commit();
                
                assertTrue("Ambient transaction should be committed", inner.isCommitted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testMandatoryPropagation_TransactionExists_CloseDoesNotCloseOuterScope() throws Exception {
        System.out.println("testMandatoryPropagation_TransactionExists_CloseDoesNotCloseOuterScope");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                
                TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.MANDATORY));
                
                //act
                inner.close();
                
                //assert
                assertTrue("inner close should revert outer", outer.isReverted());

                try{
                    outer.commit();                    
                    fail("should have thrown IllegalTransactionStateException on commit after revert");
                }catch(IllegalTransactionStateException ex){
                //expected
                }
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testMandatoryPropagationReadOnly_TransactionExists_CloseDoesNotCloseOuterScope() throws Exception {
        System.out.println("testMandatoryPropagation_TransactionExists_CloseDoesNotCloseOuterScope");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                
                TransactionScope inner = instance.openTransaction(new TransactionOptions()
                        .withPropagation(Propagation.MANDATORY)
                        .withReadOnly(true));
                
                //act
                inner.close();
                
                //assert
                assertFalse("inner close should not revert outer", outer.isReverted());

                outer.commit();                    
                assertTrue("outer should still be capable of commit", outer.isCommitted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testNeverPropagation_TransactionExists_ThrowsException() throws Exception {
        System.out.println("testNeverPropagation_TransactionExists_ThrowsException");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                
                try{
                    TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.NEVER));
                
                    fail("should have thrown TransactionPropagationException");
                }catch(TransactionPropagationException ex){
                //expected
                }
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testNeverPropagation_TransactionDoesNotExist_CurrentTransactionIsNull() throws Exception {
        System.out.println("testNeverPropagation_TransactionDoesNotExist_CurrentTransactionIsNull");
        
        try(EngineTransactionManager instance = getInstance())
        {
                
            TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.NEVER));
                
            
            Transaction current = instance.getTransaction();
            assertNull("Current TX should be null", current);
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testNotSupportedPropagation_TransactionExists_ExistingIsSuspended() throws Exception {
        System.out.println("testNotSupportedPropagation_TransactionExists_ExistingIsSuspended");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                Transaction outerTx = instance.getTransaction();
                
                //act
                try(TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.NOT_SUPPORTED))){
                
                    
                    //assert
                    Transaction current = instance.getTransaction();
                    assertNull("Current TX should be null", current);
                    
                }
                
                Transaction current = instance.getTransaction();
                assertEquals("Should restore original transaction", outerTx.getCommitId(), current.getCommitId());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());            
        }
    }
    
    @Test
    public void testNotSupportedPropagation_TransactionDoesntExist_OperatesNonTransactionally() throws Exception {
        System.out.println("testNotSupportedPropagation_TransactionDoesntExist_OperatesNonTransactionally");
        
        try(EngineTransactionManager instance = getInstance())
        {
            
            //act
            try(TransactionScope outer = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.NOT_SUPPORTED))){
                Transaction outerTx = instance.getTransaction();
                
                
                assertNull("Current TX should be null", outerTx);
                
                try(TransactionScope inner = instance.openTransaction()){
                    
                    Transaction innerTx = instance.getTransaction();
                    assertNotNull("Should have inner transaction", innerTx);
                    assertThat("Should have inner transaction", innerTx.getTransactionId(), Matchers.greaterThan(0L));
                    
                    inner.revert();
                }
                
                //assert
                outerTx = instance.getTransaction();
                assertNull("Current TX should still be null", outerTx);
                assertFalse("Revert on inner should not propagate to outer", outer.isReverted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testRequiredPropagation_TransactionDoesNotExist_CreatesNewTransaction() throws Exception {
        System.out.println("testRequiredPropagation_TransactionDoesNotExist_ThrowsException");
        
        try(EngineTransactionManager instance = getInstance())
        {
            //act
            try(TransactionScope outer = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.REQUIRED))){
                
                Transaction current = instance.getTransaction();
                
                assertNotNull("Current transaction should exist", current);
                assertThat("Should have TX ID", current.getTransactionId(), Matchers.greaterThan(0L));
                
                outer.revert();
                
                current = instance.getTransaction();
                assertTrue("Current TX should be reverted", current.isReverted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testRequiredPropagation_TransactionExists_WrapsTransaction() throws Exception {
        System.out.println("testRequiredPropagation_TransactionExists_WrapsTransaction");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                Transaction outerTx = instance.getTransaction();
                
                try (TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.REQUIRED))) {
                    
                    Transaction current = instance.getTransaction();
                
                    assertEquals("Current should be outer transaction", outerTx.getTransactionId(), current.getTransactionId());
                    
                    inner.commit();
                }
                
                //assert
                assertFalse("Commit should not yet have been finalized", outer.isCommitted());
                outer.commit();
                assertTrue("Commit should now be finalized", outer.isCommitted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testRequiresNewPropagation_NoTransactionExists_NewTransactionCreated() throws Exception {
        System.out.println("testRequiresNewPropagation_NoTransactionExists_NewTransactionCreated");
        
        try(EngineTransactionManager instance = getInstance())
        {
            
            //act
            try(TransactionScope outer = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.REQUIRES_NEW))){
             
                Transaction current = instance.getTransaction();
                
                assertNotNull("Current transaction should exist", current);
                assertThat("Should have TX ID", current.getTransactionId(), Matchers.greaterThan(0L));
                
                outer.commit();
                
                current = instance.getTransaction();
                assertTrue("Current TX should be committed", current.isCommitted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testRequiresNewPropagation_TransactionExists_TransactionIsSuspended() throws Exception {
        System.out.println("testRequiresNewPropagation_TransactionExists_TransactionIsSuspended");
       
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                Transaction outerTx = instance.getTransaction();
                
                //act
                try(TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.REQUIRES_NEW))){
                
                    
                    //assert
                    Transaction current = instance.getTransaction();
                    assertNotNull("Current transaction should exist", current);
                    assertThat("Should have TX ID", current.getTransactionId(), Matchers.greaterThan(0L));
                    assertThat("Inner TX should not be outer", current.getTransactionId(), Matchers.not(Matchers.equalTo(outerTx.getTransactionId())));
                    
                    inner.revert();
                }
                
                Transaction current = instance.getTransaction();
                assertEquals("Should restore original transaction", outerTx.getTransactionId(), current.getTransactionId());
                assertFalse("revert on inner should propagate to outer", outer.isReverted());
                assertFalse("revert on inner should propagate to outer", current.isReverted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testSupportsPropagation_TransactionDoesNotExist_ExecutesNonTransactionally() throws Exception {
        System.out.println("testSupportsPropagation_TransactionDoesNotExist_ExecutesNonTransactionally");
         
        try(EngineTransactionManager instance = getInstance())
        {
            
            //act
            try(TransactionScope outer = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.SUPPORTS))){
                
                //assert
                Transaction current = instance.getTransaction();
                assertNull("Current TX should be null", current);
                
                try(TransactionScope inner = instance.openTransaction()){
                    
                    current = instance.getTransaction();
                    assertNotNull("Should now have inner transaction", current);                    
                    
                    inner.revert();
                }
                
                //assert
                current = instance.getTransaction();
                assertNull("Current TX should be null", current);
                assertFalse("Revert on inner should not propagate to outer", outer.isReverted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    @Test
    public void testSupportsPropagation_TransactionExists_WrapsCurrentTransaction() throws Exception {
        System.out.println("testSupportsPropagation_TransactionExists_WrapsCurrentTransaction");
        
        try(EngineTransactionManager instance = getInstance())
        {
            try(TransactionScope outer = instance.openTransaction()){
                Transaction outerTx = instance.getTransaction();
                
                try (TransactionScope inner = instance.openTransaction(new TransactionOptions().withPropagation(Propagation.SUPPORTS))) {
                    
                    Transaction current = instance.getTransaction();
                
                    assertEquals("Current should be outer transaction", outerTx.getTransactionId(), current.getTransactionId());
                    
                    inner.commit();
                }
                
                //assert
                assertFalse("Commit should have not yet been finalized", outer.isCommitted());
                Transaction current = instance.getTransaction();
                assertEquals("current should be outer TX", outerTx.getTransactionId(), current.getTransactionId());
                
                outer.commit();
                assertTrue("Commit should now be finalized", outer.isCommitted());
            }
            
            assertFalse("Should not be any more open transactions", instance.anyOpenTransactions());
        }
    }
    
    
    
    //</editor-fold>
    
    private Matcher<Transaction> matchesTransaction(final Transaction tx){
        return new TypeSafeMatcher<Transaction>(){
            @Override
            protected boolean matchesSafely(Transaction item) {
                if(item.getTransactionId() != tx.getTransactionId())
                    return false;
                
                if(item.getCommitId() != tx.getCommitId())
                    return false;
                
                if(item.isCommitted() != tx.isCommitted())
                    return false;
                
                if(item.isReverted() != tx.isReverted())
                    return false;
                
                if(item.isReadOnly() != tx.isReadOnly())
                    return false;
                
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Transaction with ID").appendValue(tx.getTransactionId());
            }
            
        };
        
    }
}
