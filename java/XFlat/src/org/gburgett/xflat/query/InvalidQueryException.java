/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.query;

/**
 * This exception is thrown when a query is invalid based on one or more of
 * the indexes on a table.  
 * <p/>
 * Indexes generally ensure that the selected value
 * is of a certain type.  If a query then expects the value to be a different type,
 * then this exception is generated.
 * @author Gordon
 */
public class InvalidQueryException extends RuntimeException {
    
    private final XpathQuery query;
    
    public XpathQuery getQuery(){
        return query;
    }
    
    private final Class<?> indexType;
    public Class<?> getIndexType(){
        return indexType;
    }
    
    public InvalidQueryException(XpathQuery q, Class<?> indexType){
        super(String.format("Query %s expected value to be type %s, but index requires value to be %s", q.toString(), q.getValueType().toString(), indexType.toString()));
        
        this.query = q;
        this.indexType = indexType;
    }
}
