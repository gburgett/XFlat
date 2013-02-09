/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

/**
 *
 * @author Gordon
 */
public class UuidIdGeneratorTest extends IdGeneratorTestsBase<UuidIdGenerator>{

    @Override
    protected UuidIdGenerator getInstance() {
        return new UuidIdGenerator();
    }
    
}
