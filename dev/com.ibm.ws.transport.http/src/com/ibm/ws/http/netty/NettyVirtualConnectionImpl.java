/*******************************************************************************
 * Copyright (c) 2023, 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.http.netty;

import java.util.HashMap;
import java.util.Map;

import com.ibm.wsspi.channelfw.ConnectionDescriptor;
import com.ibm.wsspi.channelfw.VirtualConnection;

public class NettyVirtualConnectionImpl implements VirtualConnection {
    public static final NettyVirtualConnectionImpl DUMMY_NETTY_VC;

    static {
        DUMMY_NETTY_VC = new NettyVirtualConnectionImpl();
        DUMMY_NETTY_VC.init();
    }

    private Map<Object, Object> stateStore = null;
    private boolean inetAddressingValid = true;
    private ConnectionDescriptor connDesc = null;

    protected NettyVirtualConnectionImpl() {
    }

    public static NettyVirtualConnectionImpl createVC() {
        NettyVirtualConnectionImpl vc = new NettyVirtualConnectionImpl();
        vc.init();
        return vc;
    }

    public void init() {
        this.stateStore = new HashMap<Object, Object>();
    }

    @Override
    public void destroy() {
    }

    @Override
    public Map<Object, Object> getStateMap() {
        return stateStore;
    }

    @Override
    public boolean requestPermissionToRead() {
        return false;
    }

    @Override
    public boolean requestPermissionToWrite() {
        return false;
    }

    @Override
    public boolean requestPermissionToClose(long waitForPermission) {
        return false;
    }

    @Override
    public void setReadStateToDone() {
    }

    @Override
    public void setWriteStateToDone() {
    }

    @Override
    public boolean isInputStateTrackingOperational() {
        return false;
    }

    @Override
    public Object getLockObject() {
        return this;
    }

    @Override
    public boolean requestPermissionToFinishRead() {
        return false;
    }

    @Override
    public boolean requestPermissionToFinishWrite() {
        return false;
    }

    @Override
    public void setReadStatetoCloseAllowedNoSync() {
    }

    @Override
    public void setWriteStatetoCloseAllowedNoSync() {
    }

    @Override
    public boolean getCloseWaiting() {
        return false;
    }

    @Override
    public boolean isCloseWithReadOutstanding() {
        return false;
    }

    @Override
    public boolean isCloseWithWriteOutstanding() {
        return false;
    }

    @Override
    public void setInetAddressingValid(boolean _newValue) {
        this.inetAddressingValid = _newValue;
    }

    @Override
    public boolean getInetAddressingValid() {
        return this.inetAddressingValid;
    }

    @Override
    public void setConnectionDescriptor(ConnectionDescriptor _newObject) {
        this.connDesc = _newObject;
    }

    @Override
    public ConnectionDescriptor getConnectionDescriptor() {
        return this.connDesc;
    }

    @Override
    public int attemptToSetFileChannelCapable(int value) {
        return 0;
    }

    @Override
    public int getFileChannelCapable() {
        return 0;
    }

    @Override
    public boolean isFileChannelCapable() {
        return false;
    }
}
