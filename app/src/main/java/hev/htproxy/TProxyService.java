package hev.htproxy;

/** JNI bridge to hev-socks5-tunnel (TUN fd -> SOCKS5). Package/class must match the native build flags. */
public class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native void TProxyStartService(String configPath, int fd);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();
}
