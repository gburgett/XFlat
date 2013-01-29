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
package org.xflatdb.xflat.query;

import org.xflatdb.xflat.query.IntervalProvider;
import org.xflatdb.xflat.query.NumericIntervalProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Gordon
 */
public class NumericIntervalProviderTest {
    
    public NumericIntervalProviderTest() {
    }
    
    @Test
    public void testInteger_ZeroBase() throws Exception {
        System.out.println("testInteger_ZeroBase_Positive");
        
        IntervalProvider<Integer> provider = NumericIntervalProvider.forInteger(0, 100);
        
        assertEquals("[0, 100)", provider.getInterval(0).toString());
        assertEquals("[100, 200)", provider.getInterval(100).toString());
        assertEquals("[-100, 0)", provider.getInterval(-47).toString());
        assertEquals("[-300, -200)", provider.getInterval(-300).toString());
        
        assertEquals("[100, 200)", provider.nextInterval(provider.getInterval(2), 1).toString());
        assertEquals("[200, 300)", provider.nextInterval(provider.getInterval(2), 2).toString());
        assertEquals("[0, 100)", provider.nextInterval(provider.getInterval(-2), 1).toString());
        assertEquals("[-200, -100)", provider.nextInterval(provider.getInterval(2), -2).toString());
    }
    
    @Test
    public void testInteger_OffsetBase() throws Exception {
        System.out.println("testInteger_ZeroBase_Positive");
        
        IntervalProvider<Integer> provider = NumericIntervalProvider.forInteger(2, 100);
        
        assertEquals("[-98, 2)", provider.getInterval(0).toString());
        assertEquals("[102, 202)", provider.getInterval(102).toString());
        assertEquals("[-98, 2)", provider.getInterval(-47).toString());
        assertEquals("[-398, -298)", provider.getInterval(-398).toString());
        
        assertEquals("[102, 202)", provider.nextInterval(provider.getInterval(2), 1).toString());
        assertEquals("[202, 302)", provider.nextInterval(provider.getInterval(2), 2).toString());
        assertEquals("[2, 102)", provider.nextInterval(provider.getInterval(-2), 1).toString());
        assertEquals("[-198, -98)", provider.nextInterval(provider.getInterval(2), -2).toString());
    }
    
    @Test
    public void testInteger_NegativeBase() throws Exception {
        System.out.println("testInteger_ZeroBase_Positive");
        
        IntervalProvider<Integer> provider = NumericIntervalProvider.forInteger(-2, 37);
        
        assertEquals("[-2, 35)", provider.getInterval(0).toString());
        assertEquals("[35, 72)", provider.getInterval(35).toString());
        assertEquals("[-76, -39)", provider.getInterval(-47).toString());
    }
    
}
