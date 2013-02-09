/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.xflatdb.xflat.db;

/**
 *
 * @author Gordon
 */
public class TimestampIdGeneratorTest extends IdGeneratorTestsBase<TimestampIdGenerator> {

    @Override
    protected TimestampIdGenerator getInstance() {
        return new TimestampIdGenerator();
    }
    
}
