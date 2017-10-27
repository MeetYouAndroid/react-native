package com.facebook.react;

/**
 * Created by Linhh on 2017/10/27.
 */

public class MeetyouReactBridge {

  private static class Holder{
    public static MeetyouReactBridge bridge = new MeetyouReactBridge();
  }

  private MeetyouReactBridgeListener listener;

  public static MeetyouReactBridge getBridge(){
    return Holder.bridge;
  }

  public void setListener(MeetyouReactBridgeListener listener){
    this.listener = listener;
  }

  public MeetyouReactBridgeListener getListener(){
    return this.listener;
  }

}
