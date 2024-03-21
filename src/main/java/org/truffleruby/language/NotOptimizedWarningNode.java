/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.strings.TruffleString;
import org.truffleruby.RubyContext;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.core.encoding.Encodings;
import org.truffleruby.language.globals.ReadSimpleGlobalVariableNode;

/** Displays a warning when code is compiled that will compile successfully but is very low performance. We don't want
 * to bail out, as this isn't visible to users - we want them to see if they're using code like this in something like a
 * benchmark.
 *
 * Ideally you should not use this node, and instead you should optimise the code which would use it. */
public abstract class NotOptimizedWarningNode extends RubyBaseNode {

    @SuppressWarnings("serial")
    protected static final class Warned extends ControlFlowException {
    }

    @Child ReadSimpleGlobalVariableNode readVerboseNode = ReadSimpleGlobalVariableNode.create("$VERBOSE");

    public final void warn(String message) {
        executeWarn(message);
    }

    protected abstract void executeWarn(String message);

    @Specialization(rewriteOn = Warned.class)
    void warnOnce(String message) throws Warned {
        // The message should be a constant, because we don't want to do anything expensive to create it
        CompilerAsserts.partialEvaluationConstant(message);

        // If we're in the interpreter then don't warn
        if (CompilerDirectives.inInterpreter()) {
            return;
        }

        // Do not warn if $VERBOSE is nil
        if (readVerboseNode.execute() == nil) {
            return;
        }

        // Only warn if Warning[:performance] is true
        if (!getContext().getWarningCategoryPerformance().get()) {
            return;
        }

        log(message);
        throw new Warned();
    }

    @Specialization(replaces = "warnOnce")
    void doNotWarn(String message) {
        // do nothing
    }

    @TruffleBoundary
    private void log(String message) {
        // We want the topmost user source section, as otherwise lots of warnings will come from the same core methods
        final SourceSection userSourceSection = getContext().getCallStack()
                .getTopMostUserSourceSection(getEncapsulatingSourceSection());

        final String displayedWarning = String.format(
                "%s: warning: %s\n",
                getContext().fileLine(userSourceSection),
                message);

        if (DISPLAYED_WARNINGS.add(displayedWarning)) {
            var warningString = createString(TruffleString.FromJavaStringNode.getUncached(), displayedWarning,
                    Encodings.US_ASCII);
            RubyContext.send(this, coreLibrary().truffleWarningOperationsModule, "performance_warning", warningString);
        }
    }

    // The remembered set of displayed warnings is global to the VM
    private static final Set<String> DISPLAYED_WARNINGS = ConcurrentHashMap.newKeySet();
}
