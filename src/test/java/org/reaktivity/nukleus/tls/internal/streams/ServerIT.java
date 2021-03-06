/**
 * Copyright 2016-2020 The Reaktivity Project
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
package org.reaktivity.nukleus.tls.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;
import static org.reaktivity.reaktor.test.ReaktorRule.EXTERNAL_AFFINITY_MASK;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.reaktor.ReaktorConfiguration;
import org.reaktivity.reaktor.test.ReaktorRule;

public class ServerIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("route", "org/reaktivity/specification/nukleus/tls/control/route")
            .addScriptRoot("client", "org/reaktivity/specification/tls")
            .addScriptRoot("server", "org/reaktivity/specification/nukleus/tls/streams");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final ReaktorRule reaktor = new ReaktorRule()
            .directory("target/nukleus-itests")
            .commandBufferCapacity(1024)
            .responseBufferCapacity(1024)
            .counterValuesBufferCapacity(8192)
            .nukleus("tls"::equals)
            .affinityMask("target#0", EXTERNAL_AFFINITY_MASK)
            .configure(ReaktorConfiguration.REAKTOR_DRAIN_ON_CLOSE, false)
            .clean();

    @Rule
    public final TestRule chain = outerRule(reaktor).around(k3po).around(timeout);

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/connection.established/client",
        "${server}/connection.established/server" })
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/connection.established/client",
        "${server}/connection.established/server" })
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldEstablishConnectionDefaultStore() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server.alpn/controller",
            "${client}/connection.established.with.alpn/client",
            "${server}/connection.established.with.alpn/server" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNegotiateWithALPN() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/connection.established.with.alpn/client",
            "${server}/connection.established/server" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNotNegotiateALPNWithDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Ignore("https://github.com/k3po/k3po/issues/454 - Support connect aborted")
    @Test
    @Specification({
            "${route}/server.alpn/controller",
            "${client}/connection.not.established.with.wrong.alpn/client",
            "${server}/connection.established/server" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNotNegotiateWithALPNAsProtocolMismatch() throws Exception
    {
        k3po.finish();
    }

    @Ignore("https://github.com/k3po/k3po/issues/454 - Support connect aborted")
    @Test
    @Specification({
            "${route}/server.alpn/controller",
            "${client}/connection.established/client" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNegotiateWithNoALPNButRouteMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server.alpn.default/controller",
            "${client}/connection.established.with.alpn/client",
            "${server}/connection.established.with.alpn/server" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNegotiateALPNWithAlpnAndDefaultRoutes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server.alpn.default/controller",
            "${client}/connection.established/client",
            "${server}/connection.established/server" })
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldNotNegotiateALPNWithAlpnAndDefaultRoutes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/echo.payload.length.10k/client",
        "${server}/echo.payload.length.10k/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldEchoPayloadLength10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/echo.payload.length.100k/client",
        "${server}/echo.payload.length.100k/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldEchoPayloadLength100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/echo.payload.length.1000k/client",
        "${server}/echo.payload.length.1000k/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldEchoPayloadLength1000k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/server.sent.write.close.before.correlated/client",
            "${server}/server.sent.write.close.before.correlated/server"})
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveServerSentWriteCloseBeforeCorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/server.sent.write.close/client",
        "${server}/server.sent.write.close/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveServerSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/client.sent.write.close.before.correlated/client",
            "${server}/client.sent.write.close.before.correlated/server"})
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentWriteCloseBeforeCorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/client.sent.write.close.before.handshake/client"})
    public void shouldReceiveClientSentWriteCloseBeforeHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/client.sent.write.close/client",
        "${server}/client.sent.write.close/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/server.sent.write.abort/client",
        "${server}/server.sent.write.abort/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/client.sent.write.abort.before.correlated/client",
            "${server}/client.sent.write.abort.before.correlated/server"})
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentWriteAbortBeforeCorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/client.sent.write.abort/client",
        "${server}/client.sent.write.abort/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/server.sent.read.abort/client",
        "${server}/server.sent.read.abort/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${client}/client.sent.read.abort.before.correlated/client",
            "${server}/client.sent.read.abort.before.correlated/server"})
    @ScriptProperty({
            "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentReadAbortBeforeCorrelated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/client.sent.read.abort/client",
        "${server}/client.sent.read.abort/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"" })
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server/controller",
        "${client}/client.hello.malformed/client"})
    public void shouldResetMalformedClientHello() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${route}/server.want.auth/controller",
        "${client}/server.want.auth/client",
        "${server}/server.want.auth/server"})
    @ScriptProperty({
        "clientAccept \"nukleus://streams/target#0\"",
        "authorization  -2666130979403333631L" })           // "0xdb00000000000001L"
    public void serverWantClientAuth() throws Exception
    {
        k3po.finish();
    }
}
