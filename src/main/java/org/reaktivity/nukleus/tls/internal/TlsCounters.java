/**
 * Copyright 2016-2019 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.tls.internal;

import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public class TlsCounters
{
    public final LongSupplier serverDecodeNoClientHello;
    public final LongSupplier serverDecodeAcquires;
    public final LongSupplier serverDecodeReleases;
    public final LongSupplier serverEncodeAcquires;
    public final LongSupplier serverEncodeReleases;
    public final LongSupplier clientNetworkAcquires;
    public final LongSupplier clientNetworkReleases;
    public final LongSupplier clientApplicationAcquires;
    public final LongSupplier clientApplicationReleases;

    public TlsCounters(
        Function<String, LongSupplier> supplyCounter,
        Function<String, LongConsumer> supplyAccumulator)
    {
        this.serverDecodeNoClientHello = supplyCounter.apply("tls.server.decode.no.client.hello");
        this.serverDecodeAcquires = supplyCounter.apply("tls.server.decode.acquires");
        this.serverDecodeReleases = supplyCounter.apply("tls.server.decode.releases");
        this.serverEncodeAcquires = supplyCounter.apply("tls.server.encode.acquires");
        this.serverEncodeReleases = supplyCounter.apply("tls.server.encode.releases");
        this.clientNetworkAcquires = supplyCounter.apply("tls.client.network.acquires");
        this.clientNetworkReleases = supplyCounter.apply("tls.client.network.releases");
        this.clientApplicationAcquires = supplyCounter.apply("tls.client.application.acquires");
        this.clientApplicationReleases = supplyCounter.apply("tls.client.application.releases");
    }
}
