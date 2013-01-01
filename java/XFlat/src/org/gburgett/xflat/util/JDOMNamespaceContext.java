/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gburgett.xflat.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.namespace.NamespaceContext;
import org.jdom2.Namespace;

/**
 *
 * @author gordon
 */
public class JDOMNamespaceContext implements NamespaceContext {
    
    private List<Namespace> namespaces;
    
    public JDOMNamespaceContext(List<Namespace> namespaces){
        this.namespaces = new ArrayList<>(namespaces);
    }
    
    @Override
    public String getNamespaceURI(String prefix) {
        for(Namespace n : namespaces){
            if(n.getPrefix().equals(prefix)){
                return n.getURI();
            }
        }

        return null;
    }

    @Override
    public String getPrefix(String namespaceURI) {
        for(Namespace n : namespaces){
            if(n.getURI().equals(namespaceURI)){
                return n.getPrefix();
            }
        }

        return null;
    }

    @Override
    public Iterator getPrefixes(String namespaceURI) {
        List<String> ret = new ArrayList<>();
        for(Namespace n : namespaces){
            if(n.getURI().equals(namespaceURI)){
                ret.add(n.getPrefix());
            }
        }

        return ret.iterator();
    }

}
