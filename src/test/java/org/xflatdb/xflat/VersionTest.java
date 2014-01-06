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

import java.util.Date;
import org.hamcrest.Matchers;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author gordon
 */
public class VersionTest {
    
    @Test
    public void testVersion_Exists_EqualToMavenVersion() throws Exception {
        System.out.println("testVersion_Exists_EqualToMavenVersion");
        
        assertNotNull("VERSION", Version.VERSION);
        assertNotNull("VERSION_MAVEN", Version.VERSION_MAVEN);
        assertNotNull("BUILD_VERSION", Version.BUILD_VERSION);
        assertNotNull("BUILD_COMMIT", Version.BUILD_COMMIT);
        assertThat("BUILD_REVISION", Version.BUILD_REVISION, Matchers.greaterThanOrEqualTo(0));
        assertThat("BUILD_TIMESTAMP", Version.BUILD_TIMESTAMP, Matchers.greaterThan(new Date(0)));
        
        assertEquals("Maven version is not equal to commit version, please check the POM",
                Version.VERSION, Version.VERSION_MAVEN);
        
    }//end testVersion_Exists_EqualToMavenVersion
}
