import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/*
    Networklnterface类表示一个由名称和分配给此接口的IP地址列表组成的网络接口，也
    就是Networklnterface类包含网络接口名称与IP地址列表。该类提供访问网卡设备的相关
    信息，如可以获取网卡名称、IP地址和子网掩码等。
*/

public class Host {


    public static InetAddress getLocalIPV4Address() {
        InetAddress inetAddress = null;
        try {
            //获取NetworkInterface对象
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            //查找ip
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress tempaddress = addresses.nextElement();
                    //排除环回地址
                    if (!tempaddress.isLoopbackAddress()) {
                        if (tempaddress.isSiteLocalAddress()) {
                            inetAddress = tempaddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return inetAddress;
    }
}
