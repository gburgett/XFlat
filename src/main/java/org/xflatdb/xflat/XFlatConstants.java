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
package org.xflatdb.xflat;

import org.jdom2.Namespace;

/**
 * This class contains constants used by XFlat
 * @author gordon
 */
public class XFlatConstants {
    
    /**
     * The XML namespace of all control data in XFlat saved XML files.  User data
     * is stored with the default namespace, this is used for tables and IDs.
     */
    public static Namespace xFlatNs = Namespace.getNamespace("db", "http://www.xflatdb.org/xflat/db");
    
}
