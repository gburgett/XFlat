/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import org.xflatdb.xflat.Id;

/**
 * A test class annotated such that it can be marshalled by JAXB.
 * @author gordon
 */
@XmlRootElement
public class Baz {
    
    private String id;
    
    @Id
    @XmlTransient
    public String getId() {
        return this.id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    private List<String> testData = new ArrayList<>();
    
    @XmlElement
    public List<String> getTestData() {
        return this.testData;
    }
    
    private int attrInt;
    @XmlAttribute
    public int getAttrInt() {
        return this.attrInt;
    }
    
    public void setAttrInt(int attrInt) {
        this.attrInt = attrInt;
    }
}
