package com.auto.surveyplatform.transfer;



import java.io.IOException;
import java.net.Socket;

/**
 * @author: yechenyu
 * @create: 2020-02-18 00:09
 * @email: Yecynull@163.com
 * @version:
 * @descripe:
 **/
public abstract class TransferManager {

    private static TransferManager INSTANCE ;

    public static TransferManager getInstance(){
        if(INSTANCE == null){
            synchronized (TransferManager.class){
                INSTANCE = new TransferManagerImpl();
            }
        }
        return INSTANCE;
    }

    public abstract boolean initMasterDevice(Socket socket);

    public abstract boolean destoryDevice();

    public abstract RespResult translate(byte[] cmd, byte[] params, int timeout) throws SDKException;

    public abstract RespResult readClientData(int timeout) throws SDKException, IOException;

    public abstract void writeResponseData(byte[] cmd, String respCode, byte[] params) throws SDKException;
}
