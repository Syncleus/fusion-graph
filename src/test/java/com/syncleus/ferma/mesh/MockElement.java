/******************************************************************************
 * *
 * Copyright: (c) Syncleus, Inc.                                              *
 * *
 * You may redistribute and modify this source code under the terms and       *
 * conditions of the Open Source Community License - Type C version 1.0       *
 * or any later version as published by Syncleus, Inc. at www.syncleus.com.   *
 * There should be a copy of the license included with this file. If a copy   *
 * of the license is not included you are granted no right to distribute or   *
 * otherwise use this file except through a legal and valid license. You      *
 * should also contact Syncleus, Inc. at the information below if you cannot  *
 * find a license:                                                            *
 * *
 * Syncleus, Inc.                                                             *
 * 2604 South 12th Street                                                     *
 * Philadelphia, PA 19148                                                     *
 * *
 ******************************************************************************/
package com.syncleus.ferma.mesh;

import com.tinkerpop.blueprints.Element;

import java.util.Set;

public class MockElement implements Element {

    @Override
    public <T> T getProperty(final String key) {
        return null;
    }

    @Override
    public Set<String> getPropertyKeys() {
        return null;
    }

    @Override
    public void setProperty(final String key, final Object value) {

    }

    @Override
    public <T> T removeProperty(final String key) {
        return null;
    }

    @Override
    public void remove() {

    }

    @Override
    public Object getId() {
        return null;
    }
}
