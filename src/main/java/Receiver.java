import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;

public class Receiver extends Host {
    //发送方ip
    private String SenderIp;
    //发送方端口
    private int SendPort;

    private final DatagramSocket Socket;

    private byte[] Buffer;

    private final String Filename;

    private int MSS = 0;

    private int ack = 1;

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







    /*
    * @param args [0] 接收方端口号
    *      [1] 发送文件名
    * */
    public static void main () {

    }


}
