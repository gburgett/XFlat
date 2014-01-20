/*
 * Copyright 2014 XFlat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xflatdb.xflat;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Constants relating the current version of XFlat
 * @author gordon
 */
public class Version {
    /**
     * The official version assigned when the binaries were built.
     */
    public static final String VERSION;
    /**
     * The version of the binaries according to Maven.
     * This should always be the same as {@link #VERSION}, if it is not
     * then the binaries were incorrectly built.
     */
    public static final String VERSION_MAVEN;
    
    /**
     * The version assigned to the build.
     * For release builds this should be (VERSION)-0-g(commitish).  Development
     * or snapshot builds will have a revision greater than zero.
     */
    public static final String BUILD_VERSION;
    
    /**
     * The full commit hash of the source code from which these binaries were built.
     */
    public static final String BUILD_COMMIT;
    
    /**
     * The date and time when these binaries were built.
     */
    public static final Date BUILD_TIMESTAMP;
    
    /**
     * The number of revisions since the last official release.  For release builds
     * this will always be 0.  
     * For snapshot builds, this is the number of commits since the first snapshot for this version.
     * For development builds, this is the number of commits
     * since the last official release.
     */
    public static final int BUILD_REVISION;
    
    static {
        Properties props = new Properties();
        try(InputStream is = Version.class.getClassLoader().getResourceAsStream("org/xflatdb/xflat/version.properties")){
            assert(is != null);
            props.load(is);
        }
        catch(Exception ex){
            System.err.println("Unable to load XFlat version information: " + ex);
        }
        
        VERSION = props.getProperty("version", "unknown");
        VERSION_MAVEN = props.getProperty("version.mvn", "unknown");
        BUILD_VERSION = props.getProperty("build.version", "unknown");
        BUILD_COMMIT = props.getProperty("build.commit", "unknown");
        
        String val = props.getProperty("build.timestamp");
        DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        Date ts;
        try{
            ts = format.parse(val);
        }catch(ParseException ex){
            ts = new Date(0);
        }
        BUILD_TIMESTAMP = ts;
        
        val = props.getProperty("build.revision");
        int rev;
        try{
            rev = Integer.parseInt(val);
        }
        catch(NumberFormatException ex){
            rev = -1;
        }
        BUILD_REVISION = rev;
    }
    
}
