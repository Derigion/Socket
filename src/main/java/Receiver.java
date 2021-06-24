import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Date;
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
            case CLOSED:
                //握手，建立连接
                if (stpPacket.isSYN() && (stpPacket.getData() == null || stpPacket.getData().length == 0)) {
                    //根据握手报文确定发送方的地址与端口
                    this.SenderIp = hostName;
                    this.SenderPort = port;
                    this.MSS = stpPacket.getMSS();
                    this.Buffer = new byte[13 + MSS];
                    //初始ack应该由sender发送的seq决定
                    this.ACK = stpPacket.getSeq() + 1;
                    sendAck(true, false, 0, this.ACK);
                    this.receiverState = ReceiverState.ESTABLISHED;
                    System.out.println("握手成功，建立连接");
                }
                break;
            case ESTABLISHED:
                //接收数据，发送响应
                if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
                    if (stpPacket.getSeq() == ACK) {
                        //如果收到的数据报文是期望顺序中的下一个
                        System.out.println("receive:" + " time:" + new Date().toString() + " isFIN:" + stpPacket.isFIN() + " isSYN:" + stpPacket.isSYN() + " ack:" + stpPacket.getAck() + " seq:" + stpPacket.getSeq());

                        this.ACK += stpPacket.getData().length;
                        sendAck(false, false, stpPacket.getSeq(), ACK);
                        writeFile(stpPacket.getData());
                        //检查期望的下一个数据报是否已在缓存中，若在则写入文件
                        while (PacketBuffer.get(ACK) != null) {
                            System.out.println("从缓存中写入文件：" + ACK);
                            StpPacket packetInCache =PacketBuffer.get(ACK);
                            writeFile(packetInCache.getData());
                            this.ACK += packetInCache.getData().length;
                            PacketBuffer.remove(packetInCache.getSeq());
                        }
                    } else {
                        //如果收到的数据报不是期望顺序的下一个，则缓存,不改变this.ack
                        sendAck(false, false, stpPacket.getSeq(), stpPacket.getSeq() + stpPacket.getData().length);
                        PacketBuffer.put(stpPacket.getSeq(), stpPacket);
                    }
                } else if (stpPacket.isFIN() && stpPacket.getSeq() >= ACK) {
                    //发送结束完成响应
                    sendAck(false, true, 0, ++ACK);
                    this.receiverState = ReceiverState.LAST_ACK;
                }
                break;
            case LAST_ACK:
                this.receiverState = ReceiverState.FIN_CLOSED;
                System.out.println("传输结束，断开连接");
        }
    }

    /**
     * 发送响应报文
     */
    private synchronized void sendAck(boolean isSYN, boolean isFIN, int seq, int ack) {
        StpPacket stpPacket = new StpPacket(isSYN, isFIN, seq, ack, this.MSS, null);
        try {
            Socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(SenderIp), SenderPort));

        } catch (IOException e) {
            System.out.println("发送ACK失败");
            e.printStackTrace();
        }
    }

    private void writeFile(byte[] data) {
        try {
            FileOutputStream writer = new FileOutputStream(Filename, true);
            writer.write(data);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    /**
     * 控制台输入
     * @param args [0] 接收方端口
     *             [1] 文件名
     */
    public static void main (String[] args) {
        Receiver receiver = null;
        try {
            receiver = new Receiver(Integer.parseInt(args[0]), args[1]);
        } catch (SocketException e) {
            System.out.println("socket建立失败");
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("参数数量错误");
            return;
        } catch (NumberFormatException e) {
            System.out.println("参数格式错误");
            return;
        }
        System.out.println("Receiver初始化成功");
        receiver.listen();
    }

}
