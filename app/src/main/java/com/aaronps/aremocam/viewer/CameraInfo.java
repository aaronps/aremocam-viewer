/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.aaronps.aremocam.viewer;

/**
 *
 * @author krom
 */
public class CameraInfo
{
    public int width;
    public int height;
    public int type;

    @Override
    public String toString()
    {
        return "CameraInfo{" + "width=" + width + ", height=" + height + ", type=" + type + '}';
    }
}
