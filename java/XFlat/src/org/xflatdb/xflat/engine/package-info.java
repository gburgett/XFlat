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

/**
 * This package contains the implementations of {@link org.xflatdb.xflat.db.Engine} that are provided
 * by XFlat out of the box.  Custom engines can be easily added by extending
 * {@link org.xflatdb.xflat.db.EngineBase} and specifying a different {@link org.xflatdb.xflat.db.EngineFactory},
 * in the Database configuration, but these engines should be suitable for most needs.
 * The DefaultEngineFactory chooses the best engine for the file among these.
 */
package org.xflatdb.xflat.engine;
