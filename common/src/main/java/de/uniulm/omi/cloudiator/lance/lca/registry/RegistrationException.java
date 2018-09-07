/*
 * Copyright (c) 2014-2015 University of Ulm
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package de.uniulm.omi.cloudiator.lance.lca.registry;

public class RegistrationException extends Exception {


    private static final long serialVersionUID = -5059220607057764163L;

    public RegistrationException() {
        super();
    }

    public RegistrationException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public RegistrationException(String arg0) {
        super(arg0);
    }

    public RegistrationException(Throwable arg0) {
        super(arg0);
    }

}
