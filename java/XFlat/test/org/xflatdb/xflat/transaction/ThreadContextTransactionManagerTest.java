/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.transaction;

import org.xflatdb.xflat.transaction.ThreadContextTransactionManager;
import java.util.concurrent.atomic.AtomicReference;
import org.xflatdb.xflat.db.EngineTransactionManagerTestBase;
import org.xflatdb.xflat.util.FakeDocumentFileWrapper;
import org.jdom2.Document;
import org.junit.After;

/**
 *
 * @author Gordon
 */
public class ThreadContextTransactionManagerTest extends EngineTransactionManagerTestBase {
    
    private AtomicReference<Document> doc = new AtomicReference<>(null);
    
    @After
    public void tearDown(){
        doc.set(null);
    }
    
    @Override
    public ThreadContextTransactionManager getInstance(){
        return new ThreadContextTransactionManager(new FakeDocumentFileWrapper(doc));
    }
}
