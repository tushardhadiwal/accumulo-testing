/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.testing.core.randomwalk.bulk;

import java.util.Properties;

import org.apache.accumulo.testing.core.randomwalk.Environment;
import org.apache.accumulo.testing.core.randomwalk.State;
import org.apache.accumulo.testing.core.randomwalk.Test;

public abstract class BulkTest extends Test {

  @Override
  public void visit(final State state, final Environment env, Properties props) throws Exception {
    Setup.run(state, () -> {
      try {
        runLater(state, env);
      } catch (Throwable ex) {
        log.error(ex.toString(), ex);
      }
    });
  }

  abstract protected void runLater(State state, Environment env) throws Exception;

}