package com.meshchat.server.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

/**
 * Netty channel handler for mesh server connections
 * Handles connection lifecycle and message processing
 */
class MeshChannelHandler(
    private val meshServer: MeshServer
) : SimpleChannelInboundHandler<String>() {
    
    /**
     * Called when a new channel is active (client connected)
     */
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        meshServer.onClientConnected(ctx.channel())
    }
    
    /**
     * Called when a channel becomes inactive (client disconnected)
     */
    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        meshServer.onClientDisconnected(ctx.channel())
    }
    
    /**
     * Called when a message is received from client
     */
    override fun channelRead0(ctx: ChannelHandlerContext, msg: String) {
        runBlocking {
            meshServer.onMessageReceived(ctx.channel(), msg)
        }
    }
    
    /**
     * Called when an exception occurs in the channel
     */
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) { "❌ Channel exception: ${ctx.channel().remoteAddress()}" }
        ctx.close()
    }
    
    /**
     * Handle idle state events (for connection timeout and heartbeat)
     */
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            when (evt.state()) {
                IdleState.READER_IDLE -> {
                    logger.warn { "⏰ Read timeout: ${ctx.channel().remoteAddress()}" }
                    ctx.close()
                }
                IdleState.WRITER_IDLE -> {
                    logger.debug { "💓 Sending heartbeat to: ${ctx.channel().remoteAddress()}" }
                    // Heartbeat will be handled by the server logic
                }
                IdleState.ALL_IDLE -> {
                    logger.warn { "⏰ Connection idle timeout: ${ctx.channel().remoteAddress()}" }
                    ctx.close()
                }
                else -> super.userEventTriggered(ctx, evt)
            }
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }
    
    /**
     * Called when channel is registered
     */
    override fun channelRegistered(ctx: ChannelHandlerContext) {
        super.channelRegistered(ctx)
        logger.debug { "📝 Channel registered: ${ctx.channel().remoteAddress()}" }
    }
    
    /**
     * Called when channel is unregistered
     */
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        super.channelUnregistered(ctx)
        logger.debug { "📝 Channel unregistered: ${ctx.channel().remoteAddress()}" }
    }
}