/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

/**
 *
 * @author Gordon
 */
public class BigIntIdGeneratorTest extends IdGeneratorTestsBase<BigIntIdGenerator> {

    @Override
    protected BigIntIdGenerator getInstance() {
        return new BigIntIdGenerator();
    }
    
    
    
}
