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

import org.xflatdb.xflat.query.IntervalSet;
import java.util.Comparator;
import org.xflatdb.xflat.util.ComparableComparator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Gordon
 */
public class IntervalSetTest {
    
    private static Comparator<Integer> comparator = new ComparableComparator<Integer>();
    
    public IntervalSetTest() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testLt_IntervalToNegInfinity() throws Exception {
        System.out.println("testLt_IntervalToNegInfinity");
        
        IntervalSet<Integer> instance = IntervalSet.lt(17);
        
        assertEquals("(-∞, 17)", instance.toString());        
    }
    
    @Test
    public void testLTE_InclusiveOfEnd() throws Exception {
        System.out.println("testLTE_InclusiveOfEnd");
        
        IntervalSet<Integer> instance = IntervalSet.lte(-5);
        
        assertEquals("(-∞, -5]", instance.toString());
    }
    
    @Test
    public void testGT_IntervalToPosInfinity() throws Exception {
        System.out.println("testGT_IntervalToPosInfinity");
        
        IntervalSet<Integer> instance = IntervalSet.gt(107);
        
        assertEquals("(107, ∞)", instance.toString());
    }
    
    @Test
    public void testGTE_InclusiveOfBeginning() throws Exception {
        System.out.println("testGTE_InclusiveOfBeginning");
        
        IntervalSet<Integer> instance = IntervalSet.gte(-4);
        
        assertEquals("[-4, ∞)", instance.toString());
    }
    
    @Test
    public void testEQ_ExactValue() throws Exception {
        System.out.println("testEQ_ExactValue");
        
        IntervalSet<Integer> instance = IntervalSet.eq(0);
        
        assertEquals("[0, 0]", instance.toString());
    }
    
    @Test
    public void testNE_AllOtherValues() throws Exception {
        System.out.println("testNE_AllOtherValues");
        
        IntervalSet<Integer> instance = IntervalSet.ne(42);
        
        assertEquals("(-∞, 42) U (42, ∞)", instance.toString());
    }
    
    @Test
    public void testAll_NegToPosInfinity() throws Exception {
        System.out.println("testAll_NegToPosInfinity");
        
        IntervalSet<Integer> instance = IntervalSet.all();
        
        assertEquals("(-∞, ∞)", instance.toString());
    }
    
    @Test
    public void testUnion_NoIntersections_HasBothIntervals() throws Exception {
        System.out.println("testUnion_NoIntersections_HasBothIntervals");
        
        IntervalSet<Integer> a = IntervalSet.lt(-1);
        IntervalSet<Integer> b = IntervalSet.gt(2);
        
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-∞, -1) U (2, ∞)", instance.toString());
    }
    
    @Test
    public void testUnion_Intersection_CombinesIntervals() throws Exception {
        System.out.println("testUnion_Intersection_CombinesIntervals");
        
        IntervalSet<Integer> a = IntervalSet.lt(2);
        IntervalSet<Integer> b = IntervalSet.gt(-1);
        
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-∞, ∞)", instance.toString());
    }
    
    @Test
    public void testUnion_Touching_CombinesIntervals() throws Exception {
        System.out.println("testUnion_Intersection_CombinesIntervals");
        
        IntervalSet<Integer> a = IntervalSet.lt(2);
        IntervalSet<Integer> b = IntervalSet.gte(2);
        
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-∞, ∞)", instance.toString());
    }
    
    @Test
    public void testUnion_OneIntervalContained_HasOuterInterval() throws Exception {
        System.out.println("testUnion_OneIntervalContained_HasOuterInterval");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(0, 5);
        
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-10, 10)", instance.toString());
    }
    
    @Test
    public void testUnion_EqualIntervals_HasOneInterval() throws Exception {
        System.out.println("testUnion_EqualIntervals_HasOneInterval");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(-10, 10);
        
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-10, 10)", instance.toString());
    }
    
    @Test
    public void testIntersection_NoIntersections_Nothing() throws Exception {
        System.out.println("testIntersection_NoIntersections_Nothing");
        
        IntervalSet<Integer> a = IntervalSet.lt(-1);
        IntervalSet<Integer> b = IntervalSet.gt(1);
        
        IntervalSet<Integer> instance = a.intersection(b, comparator);
        
        assertEquals("", instance.toString());
    }
    
    @Test
    public void testIntersection_Intersection_HasIntersection() throws Exception {
        System.out.println("testIntersection_NoIntersections_Nothing");
        
        IntervalSet<Integer> a = IntervalSet.lt(1);
        IntervalSet<Integer> b = IntervalSet.gt(-1);
        
        IntervalSet<Integer> instance = a.intersection(b, comparator);
        
        assertEquals("(-1, 1)", instance.toString());
    }
    
    @Test
    public void testIntersection_OneIntervalContained_HasInnerInterval() throws Exception {
        System.out.println("testIntersection_OneIntervalContained_HasInnerInterval");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(0, 5);
        
        IntervalSet<Integer> instance = a.intersection(b, comparator);
        
        assertEquals("(0, 5)", instance.toString());
    }
    
    @Test
    public void testIntersection_OneIntervalMultipleIntersections_HasAll() throws Exception {
        System.out.println("testIntersection_OneIntervalMultipleIntersections_HasAll");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(0, 5);
        IntervalSet<Integer> c = IntervalSet.between(9, 11);
        
        b = b.union(c, comparator);
        
        //act
        IntervalSet<Integer> instance = a.intersection(b, comparator);
        
        assertEquals("(0, 5) U (9, 10)", instance.toString());
    }
    
    @Test
    public void testIntersection_EqualIntervals_HasOneInterval() throws Exception {
        System.out.println("testIntersection_EqualIntervals_HasOneInterval");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(-10, 10);
        
        //act
        IntervalSet<Integer> instance = a.intersection(b, comparator);
        
        assertEquals("(-10, 10)", instance.toString());
    }
    
    @Test
    public void testUnion_MultipleIntersections_HasOneInterval() throws Exception {
        System.out.println("testUnion_MultipleIntersections_HasOneInterval");
        
        IntervalSet<Integer> a = IntervalSet.between(-10, 10);
        IntervalSet<Integer> b = IntervalSet.between(0, 5);
        IntervalSet<Integer> c = IntervalSet.between(9, 11);
        
        b = b.union(c, comparator);
        
        //act
        IntervalSet<Integer> instance = a.union(b, comparator);
        
        assertEquals("(-10, 11)", instance.toString());
    }
    
    @Test
    public void testUnionAndIntersection_MultipleIntersectionsAndNonIntersecting_CombinesIntervals() throws Exception {
        System.out.println("testUnionAndIntersection_MultipleIntersectionsAndNonIntersecting_CombinesIntervals");
        
        IntervalSet<Integer> a = IntervalSet.lte(12);
        IntervalSet<Integer> b = IntervalSet.between(0, 5);
        IntervalSet<Integer> c = IntervalSet.between(9, 11);
        IntervalSet<Integer> d = IntervalSet.gt(11);
        IntervalSet<Integer> e = IntervalSet.eq(11);
        
        // (0, 5) U (9, 11)
        b = b.union(c, comparator);
        
        // { (0, 5) U (9, 11) } U (11, ∞)
        d = b.union(d, comparator);
        
        // (-∞, 12] ∩ { (0, 5) U (9, 11) U (11, ∞) }
        a = a.intersection(d, comparator);
        
        assertEquals("(0, 5) U (9, 11) U (11, 12]", a.toString());
        
        // { (0, 5) U (9, 11) U (11, 12] } U [11, 11)
        a = a.union(e, comparator);
        
        assertEquals("(0, 5) U (9, 12]", a.toString());
    }
    
    @Test
    public void testIntersects_IntersectingValues_True() throws Exception {
        System.out.println("testIntersects_IntersectingValues_True");
        
        IntervalSet<Integer> a = IntervalSet.between(1, 3);
        IntervalSet<Integer> b = IntervalSet.between(2, 4);
        
        assertTrue("should intersect", a.intersects(b, comparator));
        assertTrue("should intersect", b.intersects(a, comparator));
    }//end testIntersects_IntersectingValues_True
    
    @Test
    public void testIntersects_NonIntersecting_False() throws Exception {
        System.out.println("testIntersects_NonIntersecting_False");
        
        IntervalSet<Integer> a = IntervalSet.between(1, 2);
        IntervalSet<Integer> b = IntervalSet.between(3, 4);
        
        assertFalse("should not intersect", a.intersects(b, comparator));
        assertFalse("should not intersect", b.intersects(a, comparator));
    }//end testIntersects_NonIntersecting_False
    
    @Test
    public void testIntersects_NonInclusiveOfSingleValue_False() throws Exception {
        System.out.println("testIntersects_NonInclusiveOfSingleValue_False");
        
        IntervalSet<Integer> a = IntervalSet.eq(1);
        IntervalSet<Integer> b = IntervalSet.gt(1);
        
        assertFalse("should not intersect", a.intersects(b, comparator));
        assertFalse("should not intersect", b.intersects(a, comparator));
    }//end testIntersects_NonInclusiveOfSingleValue_False
    
    @Test
    public void testIntersects_InclusiveOfSingleValue_True() throws Exception {
        System.out.println("testIntersects_InclusiveOfSingleValue_True");
        
        IntervalSet<Integer> a = IntervalSet.eq(-3);
        IntervalSet<Integer> b = IntervalSet.lte(-3);
        
        assertTrue("should intersect", a.intersects(b, comparator));
        assertTrue("should intersect", b.intersects(a, comparator));
    }//end testIntersects_InclusiveOfSingleValue_True
}
