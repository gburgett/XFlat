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
package test;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
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
@XStreamAlias("baz")
public class Baz {
    
    @XStreamOmitField
    private String id;
    
    @Id
    @XmlTransient    
    public String getId() {
        return this.id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    @XStreamImplicit(itemFieldName="testData")
    private List<String> testData = new ArrayList<>();
    
    @XmlElement
    public List<String> getTestData() {
        return this.testData;
    }
    
    @XStreamAsAttribute
    private int attrInt;
    
    @XmlAttribute    
    public int getAttrInt() {
        return this.attrInt;
    }
    
    public void setAttrInt(int attrInt) {
        this.attrInt = attrInt;
    }
}
