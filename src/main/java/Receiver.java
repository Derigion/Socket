import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;

public class Receiver extends Host {
    //发送方ip
    private String SenderIp;
    //发送方端口
    private int SenderPort;

    private final DatagramSocket Socket;
    //缓冲区
    private byte[] Buffer;

    private final String Filename;
    //最大报文段长度
    private int MSS = 0;
    //确认字符
    private int ACK = 1;

    //seq与报文的映射
    private final HashMap<Integer, StpPacket> PacketBuffer;

    private ReceiverState receiverState = ReceiverState.CLOSED;

    public Receiver(int receiverPort, String filename) throws SocketException {
        //发送文件存于resource目录下
        Filename = "./resource/" + filename;
        Socket = new DatagramSocket(receiverPort, getLocalIPV4Address());
        Buffer = new byte[13];
        PacketBuffer = new HashMap<>();
    }


    private void listen() {
        System.out.println("开始监听接收报文");
        while (receiverState != ReceiverState.FIN_CLOSED) {
            receive();
        }
    }

    private void receive() {
        DatagramPacket datagramPacket = new DatagramPacket(Buffer, Buffer.length);
        try {
            Socket.receive(datagramPacket);
            int len = datagramPacket.getLength();
            byte[] data = new byte[len];
            if (len >= 0)
                System.arraycopy(Buffer, 0, data, 0, len);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 对收到的报文进行响应
     * 处理断开连接和建立连接时的握手请求与发送
     * 将受到的数据报文拆包并写入文件
     *
     * @param stpPacket 收到的报文
     * @param hostName  主机IP
     * @param port      端口
     * @return
     */
    private void handleReceivePacket(StpPacket stpPacket, String hostName, int port) {
        switch (this.receiverState) {
            //建立连接
            case CLOSED:
                if (stpPacket.isSYN()) {
                    if (stpPacket.getData() == null || stpPacket.getData().length == 0) {
                        this.SenderIp = hostName;
                        this.SenderPort = port;
                        this.MSS = stpPacket.getMSS();
                        this.Buffer = new byte[13 + MSS];

                    }
                }
        }
    }






    /*
    * @param args [0] 接收方端口号
    *      [1] 发送文件名
    * */
    public static void main () {

    }


}
