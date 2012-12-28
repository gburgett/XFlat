/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.db;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gburgett.xflat.convert.ConversionException;
import org.gburgett.xflat.convert.Converter;
import org.jdom2.Element;

/**
 * Represents the configuration of one table. 
 * Specifies indexes, an ID generator, sharding, etc.
 * 
 * This class is immutable: all set methods return new objects.
 * @author gordon
 */
public class TableConfig {
 
    private TableConfig(){
    }
    
    private TableConfig(TableConfig other){
        this.idGenerator = other.idGenerator;
    }
    
    private Class<? extends IdGenerator> idGenerator;
    /**
     * Gets the ID generator class used for this table.
     * @return 
     * @see #setIdGenerator(java.lang.Class) 
     */
    public Class<? extends IdGenerator> getIdGenerator(){
        return this.idGenerator;
    }
    /**
     * Sets the ID generator class used for this table.  Overrides the default
     * ID generation strategy to specify a specific ID generator.
     * 
     * A Null value indicates that the Database should select the appropriate
     * generator based on its IdGenerator Strategy.
     * @param idGenerator The class of ID generator to use.
     * @return A new instance with the idGenerator property set.
     */
    public TableConfig setIdGenerator(Class<? extends IdGenerator> idGenerator){
        TableConfig ret = new TableConfig(this);
        ret.idGenerator = idGenerator;
        return ret;
    }
    
    //TODO: future configuration options
    
    /**
     * The default configuration used by the database when no configuration
     * is specified.
     */
    public static TableConfig defaultConfig = new TableConfig()
            .setIdGenerator(null);

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.idGenerator);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TableConfig other = (TableConfig) obj;
        if (!Objects.equals(this.idGenerator, other.idGenerator)) {
            return false;
        }
        return true;
    }
    
    
    public static Converter<TableConfig, Element> ToElementConverter = new Converter<TableConfig, Element>(){
        @Override
        public Element convert(TableConfig source) throws ConversionException {
            Element ret = new Element("config", XFlatDatabase.xFlatNs);
            if(source.idGenerator != null)
                ret.setAttribute("idGenerator", source.idGenerator.getName(), XFlatDatabase.xFlatNs);
            return ret;
        }
    };
    
    public static Converter<Element, TableConfig> FromElementConverter = new Converter<Element, TableConfig>(){
        @Override
        public TableConfig convert(Element source) throws ConversionException {
            if(!"config".equals(source.getName()) || !XFlatDatabase.xFlatNs.equals(source.getNamespace())){
                throw new ConversionException("Expected element named config in namespace " + XFlatDatabase.xFlatNs);
            }
            
            TableConfig ret = new TableConfig();
            String idGenerator = source.getAttributeValue("idGenerator", XFlatDatabase.xFlatNs);
            if(idGenerator != null){
                try {
                    Class<?> cl = this.getClass().getClassLoader().loadClass(idGenerator);
                    ret.idGenerator = (Class<? extends IdGenerator>)cl;
                } catch (ClassNotFoundException ex) {
                    throw new ConversionException("Could not load ID generator", ex);
                }
            }
            return ret;
        }
    };
}
