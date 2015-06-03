@Grapes(
        @Grab(group='io.netty', module='netty', version='3.9.1.Final')
)
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.ChannelFactory
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder
import org.jboss.netty.handler.codec.frame.Delimiters
import org.jboss.netty.handler.codec.string.StringDecoder
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.util.CharsetUtil
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
@Grapes(
        @Grab(group='args4j', module='args4j', version='2.0.31')
)
import org.kohsuke.args4j.Option
import utils.SSLContextShim

import java.util.concurrent.Executors


def opts = new ServerOptions()
def cmdline = new CmdLineParser(opts)
try {
    cmdline.parseArgument(args)
} catch (CmdLineException e) {
    println e.message
    cmdline.printUsage(System.out)
    System.exit(1)
}

new ServerImpl(opts).start { msg ->
    println "RECEIVED: ${msg}"
}

class ServerImpl {
    private ServerOptions options

    ServerImpl(ServerOptions options) {
        this.options = options
    }

    void start(Closure consumer) {
        ChannelFactory factory =
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());

        def serverBootstrap = new ServerBootstrap(factory)
        serverBootstrap.setOption("child.tcpNoDelay", true);

        serverBootstrap.pipelineFactory = new SecurityOptionalPipelineFactory(options, consumer)

        println "Ready to accept connections on port ${options.port}..."
        serverBootstrap.bind(new InetSocketAddress(options.port))
    }
}

class SecurityOptionalPipelineFactory implements ChannelPipelineFactory {
    private SSLContextShim sslContext
    private final Closure consumer

    SecurityOptionalPipelineFactory(ServerOptions options, Closure consumer) {
        this.consumer = consumer
        if (options.useSsl()) {
            sslContext = SSLContextShim.createServerContext(options.certChainFile, options.keyFile)
        }
    }

    @Override
    ChannelPipeline getPipeline() throws Exception {
        def p = Channels.pipeline()
        if (sslContext) {
            p.addLast('ssl', new SslHandler(sslContext.createEngine()))
        }
        p.addLast('framer', new DelimiterBasedFrameDecoder(1500, Delimiters.lineDelimiter()))
        p.addLast('decoder', new StringDecoder(CharsetUtil.UTF_8))
        p.addLast('handler', new UpstreamHandler(consumer))
        return p
    }

}


class ServerOptions {
    @Option(name='-cert', required = false, usage='The public certificate (aka chain file)')
    File certChainFile

    @Option(name='-key', required = false, usage='The private key file in pkcs8 encoding (not PEM)')
    File keyFile

    @Option(name='-port', required = true, usage='The port where the server will accept connections')
    int port

    boolean useSsl() {
        certChainFile && keyFile
    }
}

class UpstreamHandler extends SimpleChannelUpstreamHandler {
    private Closure consumer

    UpstreamHandler(Closure consumer) {
        this.consumer = consumer
    }

    @Override
    void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        consumer.call(e.message)
        super.messageReceived(ctx, e)
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        System.err.println "Exception: ${e.cause} on ${e.channel.remoteAddress}"
        ctx.channel.close()
    }
}