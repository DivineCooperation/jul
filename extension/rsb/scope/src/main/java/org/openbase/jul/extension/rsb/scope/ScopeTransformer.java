package org.openbase.jul.extension.rsb.scope;

/*
 * #%L
 * JUL Extension RSB Scope
 * %%
 * Copyright (C) 2015 - 2021 openbase.org
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

import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.CouldNotTransformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.extension.type.processing.ScopeProcessor;
import rsb.Scope;
import org.openbase.type.communication.ScopeType;

import java.util.Collection;

/**
 * * @author Divine <a href="mailto:DivineThreepwood@gmail.com">Divine</a>
 */
public class ScopeTransformer {

    public static rsb.Scope transform(final ScopeType.Scope scope) throws CouldNotTransformException {
        try {
            if (scope == null) {
                throw new NotAvailableException("scope");
            }            
            return new Scope(ScopeProcessor.generateStringRep(scope.getComponentList()));
        } catch (Exception ex) {
            throw new CouldNotTransformException(scope, rsb.Scope.class, ex);
        }
    }

    public static ScopeType.Scope transform(final rsb.Scope scope) throws CouldNotTransformException {
        try {
            if (scope == null) {
                throw new NotAvailableException("scope");
            }
            if (scope.getComponents().isEmpty()) {
                throw new NotAvailableException("components");
            }
            return ScopeType.Scope.newBuilder().addAllComponent(scope.getComponents()).build();
        } catch (Exception ex) {
            throw new CouldNotTransformException(scope, rsb.Scope.class, ex);
        }
    }
}
