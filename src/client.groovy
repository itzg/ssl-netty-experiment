import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.string.StringEncoder
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.util.CharsetUtil
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import utils.SSLContextShim

import java.util.concurrent.TimeUnit

def opts = new ClientOptions()
def cmdline = new CmdLineParser(opts)
try {
    cmdline.parseArgument(args)
} catch (CmdLineException e) {
    println e.message
    cmdline.printUsage(System.out)
    System.exit(1)
}

new ClientImpl(options:opts).start()

class ClientImpl {
    private ClientOptions options

    void start() {

        def bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory())
        bootstrap.pipelineFactory = new SecurityOptionalClientPipelineFactory(options)

        def channelFuture = bootstrap.connect(new InetSocketAddress(options.host, options.port)).await()
        if (channelFuture.success) {
            def channel = channelFuture.channel
            sendTo(channel)
            channel.close()
        }

        bootstrap.releaseExternalResources()
    }

    private void sendTo(Channel ch) {
        println "Connected. Send some lines to the server:"
        def console = System.console()
        if (console) {
            String line
            while (line = console.readLine()) {
                ch.write(line)
                ch.write('\n')
            }
        }
        else {
            System.in.withReader {
                String line
                while (line = it.readLine()) {
                    ch.write(line)
                    ch.write('\n')
                }
            }
        }
    }
}

class SecurityOptionalClientPipelineFactory implements ChannelPipelineFactory {
    private SSLContextShim sslContext

    SecurityOptionalClientPipelineFactory(ClientOptions options) {
        if (options.useSsl) {
            sslContext = SSLContextShim.createClientContext()
        }
    }

    @Override
    ChannelPipeline getPipeline() throws Exception {
        def p = Channels.pipeline()
        if (sslContext) {
            p.addLast('ssl', new SslHandler(sslContext.createEngine()))
        }
        p.addLast('upstream', new SimpleChannelUpstreamHandler() {
            @Override
            void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
                System.err.println "OOPS: ${e}"
                ctx.channel.close()
                System.exit(1)
            }
        })
        p.addLast('encoding', new StringEncoder(CharsetUtil.UTF_8))
        return p
    }
}

class ClientOptions {
    @Option(name = '-host', required = true, usage = 'Remote host to access')
    String host

    @Option(name = '-port', required = true, usage = 'Remote TCP port to access')
    int port

    @Option(name = '-ssl', usage = 'Enable SSL/TLS')
    boolean useSsl

    @Option(name = '-timeout', usage='Amount of seconds to try to connect to server')
    int timeoutSec = 10
}