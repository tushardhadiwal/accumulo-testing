// /*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements. See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License. You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package org.apache.accumulo.testing.healthprobe;

// import java.util.Map.Entry;

// import org.apache.accumulo.core.client.Accumulo;
// import org.apache.accumulo.core.client.AccumuloClient;
// import org.apache.accumulo.core.client.Scanner;
// import org.apache.accumulo.core.client.TableNotFoundException;
// import org.apache.accumulo.core.data.Key;
// import org.apache.accumulo.core.data.Value;
// import org.apache.accumulo.core.security.Authorizations;
// import org.apache.accumulo.testing.cli.ClientOpts;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// /**
// * Reads all data between two rows
// */
// public class Read {

// private static final Logger log = LoggerFactory.getLogger(Read.class);

// public static void main(String[] args) throws TableNotFoundException {
// ClientOpts opts = new ClientOpts();
// opts.parseArgs(Read.class.getName(), args);

// try (AccumuloClient client =
// Accumulo.newClient().from(opts.getClientPropsPath()).build();
// Scanner scan = client.createScanner("hellotable", Authorizations.EMPTY))

// {
// long startTime = System.nanoTime();
// // scan.setRange(new Range(new Key("row_0"), new Key("row_1002")));
// for (Entry<Key, Value> e : scan) {
// Key key = e.getKey();
// startTime = System.nanoTime();
// log.info(key.getRow() + " " + key.getColumnFamily() + " " +
// key.getColumnQualifier() + " "
// + e.getValue());
// long stopTime = System.nanoTime();
// System.out.println(stopTime - startTime);
// }
// }
// }
// }

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
package org.apache.accumulo.testing.healthprobe;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class Readprobe {

  private static final Logger log = LoggerFactory.getLogger(Readprobe.class);

  public static void main(String[] args) throws Exception {
    ScanOpts opts = new ScanOpts();
    opts.parseArgs(Readprobe.class.getName(), args);

    try (AccumuloClient client = Accumulo.newClient().from(opts.getClientProps()).build();
        Scanner scanner = client.createScanner(opts.tableName, new Authorizations())) {
      if (opts.isolate) {
        scanner.enableIsolation();
      }
      int scannerSleepMs = 60000;
      LoopControl scanning_condition = opts.continuous ? new ContinuousLoopControl()
          : new IterativeLoopControl(opts.scan_iterations);

      while (scanning_condition.keepScanning()) {
        Random tablet_index_generator = new Random(opts.scan_seed);
        Range range = pickRange(client.tableOperations(), opts.tableName, tablet_index_generator);
        scanner.setRange(range);
        if (opts.batch_size > 0) {
          scanner.setBatchSize(opts.batch_size);
        }
        try {
          long startTime = System.nanoTime();
          int count = consume(scanner);
          long stopTime = System.nanoTime();
          // System.out.println(stopTime - startTime);
          log.debug("SCN {} {} {} {}", startTime, tablet_index_generator, (stopTime - startTime),
              count);

          if (scannerSleepMs > 0) {
            sleepUninterruptibly(scannerSleepMs, TimeUnit.MILLISECONDS);
          }
        } catch (Exception e) {
          System.err.println(String.format(
              "Exception while scanning range %s. Check the state of Accumulo for errors.", range));
          throw e;
        }
      }
    }
  }

  public static int consume(Iterable<Entry<Key,Value>> iterable) {
    Iterator<Entry<Key,Value>> itr = iterable.iterator();
    int count = 0;
    while (itr.hasNext()) {
      Entry<Key,Value> e = itr.next();
      Key key = e.getKey();
      // System.out.println(key.getRow() + " " + key.getColumnFamily() + " " +
      // key.getColumnQualifier() + " " + e.getValue());
      itr.next();
      count++;
    }
    return count;
  }

  public static Range pickRange(TableOperations tops, String table, Random r)
      throws TableNotFoundException, AccumuloSecurityException, AccumuloException {
    ArrayList<Text> splits = Lists.newArrayList(tops.listSplits(table));
    if (splits.isEmpty()) {
      return new Range();
    } else {
      int index = r.nextInt(splits.size());
      Text endRow = splits.get(index);
      Text startRow = index == 0 ? null : splits.get(index - 1);
      return new Range(startRow, false, endRow, true);
    }
  }

  /*
   * These interfaces + implementations are used to determine how many times the scanner should look
   * up a random tablet and scan it.
   */
  static interface LoopControl {
    public boolean keepScanning();
  }

  // Does a finite number of iterations
  static class IterativeLoopControl implements LoopControl {
    private final int max;
    private int current;

    public IterativeLoopControl(int max) {
      this.max = max;
      this.current = 0;
    }

    @Override
    public boolean keepScanning() {
      if (current < max) {
        ++current;
        return true;
      } else {
        return true;
      }
    }
  }

  // Does an infinite number of iterations
  static class ContinuousLoopControl implements LoopControl {
    @Override
    public boolean keepScanning() {
      return true;
    }
  }
}
