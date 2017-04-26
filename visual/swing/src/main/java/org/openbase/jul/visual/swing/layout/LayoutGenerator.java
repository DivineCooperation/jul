package org.openbase.jul.visual.swing.layout;

/*
 * #%L
 * JUL Visual Swing
 * %%
 * Copyright (C) 2015 - 2017 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;

import java8.util.function.Consumer;
import java8.util.function.Function;
import java8.util.stream.StreamSupport;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public final class LayoutGenerator {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LayoutGenerator.class);

    public static final int DEFAULT_GAP_TYPE = -1;
    public static final int ZERO_GAP_TYPE = -2;

    private LayoutGenerator() {
    }

    public static GroupLayout generateHorizontalLayout(final JPanel panel, final Collection<? extends JComponent> componentCollection) {
        return generateHorizontalLayout(panel, componentCollection, DEFAULT_GAP_TYPE);
    }

    public static GroupLayout generateHorizontalLayout(final JPanel panel, final Collection<? extends JComponent> componentCollection, final int gapSize) {
        final GroupLayout listLayout = new GroupLayout(panel);
        try {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (panel) {
                        assert panel != null;
                        assert componentCollection != null;
                        panel.removeAll();

                        panel.setLayout(listLayout);
                        final GroupLayout.ParallelGroup parallelGroup = listLayout.createParallelGroup(Alignment.LEADING);

                        // return if list is components empty
                        if (componentCollection.isEmpty()) {
                            return;
                        }

                        StreamSupport.stream(componentCollection).forEach(new Consumer<JComponent>() {
                                                                              @Override
                                                                              public void accept(JComponent component) {
                                                                                  parallelGroup.addComponent(component, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
                                                                              }
                                                                          });
                        listLayout.setHorizontalGroup(parallelGroup);

                        final GroupLayout.SequentialGroup sequentialGroup = listLayout.createSequentialGroup();
                        for (JComponent component : componentCollection) {
                            sequentialGroup.addComponent(component);
                            switch (gapSize) {
                                case ZERO_GAP_TYPE:
                                    break;
                                case DEFAULT_GAP_TYPE:
                                    sequentialGroup.addPreferredGap(ComponentPlacement.RELATED);
                                    break;
                                default:
                                    sequentialGroup.addPreferredGap(ComponentPlacement.RELATED, gapSize, gapSize);
                            }
                        }
                        listLayout.setVerticalGroup(listLayout.createParallelGroup(Alignment.LEADING).addGroup(sequentialGroup));
                    }
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                runnable.run();
            } else {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        runnable.run();
                    }
                });
            }
        } catch (InterruptedException | InvocationTargetException ex) {
            ExceptionPrinter.printHistory(ex, LOGGER, LogLevel.WARN);
        }
        return listLayout;
    }

    public static GroupLayout generateVerticalLayout(final JPanel panel, final Collection<? extends JComponent> componentCollection) {
        return generateVerticalLayout(panel, componentCollection, DEFAULT_GAP_TYPE);
    }

    public static GroupLayout generateVerticalLayout(final JPanel panel, final Collection<? extends JComponent> componentCollection, final int gabSize) {
        final GroupLayout lineLayout = new GroupLayout(panel);
        try {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (panel) {
                        assert panel != null;
                        assert componentCollection != null;
                        panel.removeAll();

                        panel.setLayout(lineLayout);

                        final ParallelGroup parallelGroup = lineLayout.createParallelGroup(Alignment.LEADING);
                        final SequentialGroup sequentialGroup = lineLayout.createSequentialGroup();

                        StreamSupport.stream(componentCollection).map(new Function<JComponent, Object>() {
                            @Override
                            public Object apply(JComponent component) {
                                sequentialGroup.addComponent(component);
                                return component;
                            }
                        }).forEach(new Consumer<Object>() {
                            @Override
                            public void accept(Object o) {
                                sequentialGroup.addGap(0, 0, 0);
                            }
                        });

                        parallelGroup.addGroup(sequentialGroup);
                        lineLayout.setHorizontalGroup(parallelGroup);

                        final ParallelGroup outerParallelGroup = lineLayout.createParallelGroup(Alignment.LEADING);
                        final ParallelGroup innerParallelGroup = lineLayout.createParallelGroup(Alignment.BASELINE);
                        StreamSupport.stream(componentCollection).forEach(new Consumer<JComponent>() {
                                                                              @Override
                                                                              public void accept(JComponent component) {
                                                                                  innerParallelGroup.addComponent(component);
                                                                              }
                                                                          });
                        outerParallelGroup.addGroup(innerParallelGroup);
                        lineLayout.setVerticalGroup(outerParallelGroup);
                    }
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                runnable.run();
            } else {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        runnable.run();
                    }
                });
            }
        } catch (InterruptedException | InvocationTargetException ex) {
            ExceptionPrinter.printHistory(ex, LOGGER, LogLevel.WARN);
        }
        return lineLayout;
    }

}
