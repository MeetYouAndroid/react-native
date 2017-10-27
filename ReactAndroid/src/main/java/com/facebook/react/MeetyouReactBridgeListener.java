package com.facebook.react;

/**
 * Created by Linhh on 2017/10/27.
 */

public interface MeetyouReactBridgeListener {
  public int getInt(String name, int orginInt);
  public boolean getBoolean(String name, boolean orginBoolean);
  public double getDouble(String name, double orginDouble);
  public float getFloat(String name, float orginFloat);
  public String getString(String name,String orginString);
}
