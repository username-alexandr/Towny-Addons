package cc.crafto.ui.client;
import cc.crafto.ui.Protocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WirePayload(byte[] bytes) implements CustomPacketPayload {
    public static final Type<WirePayload> TYPE=new Type<>(Identifier.parse(Protocol.CHANNEL));
    public static final StreamCodec<RegistryFriendlyByteBuf,WirePayload> CODEC=new StreamCodec<>() {
        public WirePayload decode(RegistryFriendlyByteBuf b){
            int n=b.readableBytes();
            if(n>Protocol.MAX_BYTES){b.skipBytes(n);return new WirePayload(new byte[0]);}
            byte[] bytes=new byte[n];b.readBytes(bytes);return new WirePayload(bytes);
        }
        public void encode(RegistryFriendlyByteBuf b,WirePayload p){if(p.bytes.length>Protocol.MAX_BYTES)throw new IllegalArgumentException("CraftoUI payload too large");b.writeBytes(p.bytes);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
