/*
 * This file is part of Dimdwarf Application Server <http://dimdwarf.sourceforge.net/>
 *
 * Copyright (c) 2008, Esko Luontola. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 *     * Neither the name of the copyright holder nor the names of its contributors
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.orfjackal.dimdwarf.scheduler;

import com.google.inject.*;
import jdave.*;
import jdave.junit4.JDaveRunner;
import net.orfjackal.dimdwarf.api.EntityInfo;
import net.orfjackal.dimdwarf.api.internal.EntityObject;
import net.orfjackal.dimdwarf.entities.*;
import net.orfjackal.dimdwarf.modules.*;
import net.orfjackal.dimdwarf.tasks.TaskExecutor;
import org.junit.runner.RunWith;

import java.io.Serializable;

/**
 * @author Esko Luontola
 * @since 26.11.2008
 */
@RunWith(JDaveRunner.class)
@Group({"fast"})
public class RecoverableSetSpec extends Specification<Object> {

    private static final String PREFIX = "prefix";

    private TaskExecutor taskContext;
    private Provider<BindingStorage> bindings;
    private Provider<EntityInfo> entities;

    private RecoverableSet<StoredValue> set;

    private StoredValue value1 = new StoredValue("1");
    private StoredValue value2 = new StoredValue("2");

    public void create() throws Exception {
        Injector injector = Guice.createInjector(
                new EntityModule(),
                new DatabaseModule(),
                new TaskContextModule()
        );
        taskContext = injector.getInstance(TaskExecutor.class);
        bindings = injector.getProvider(BindingStorage.class);
        entities = injector.getProvider(EntityInfo.class);
        specify(thereMayBeBindingsInOtherNamespaces());

        set = new RecoverableSet<StoredValue>(PREFIX, bindings, entities);
    }

    private boolean thereMayBeBindingsInOtherNamespaces() {
        taskContext.execute(new Runnable() {
            public void run() {
                bindings.get().update("a.shouldNotTouchThis", new DummyEntity());
                bindings.get().update("z.shouldNotTouchThis", new DummyEntity());
            }
        });
        return true;
    }


    public class WhenARecoverableSetIsEmpty {

        public void itContainsNoObjects() {
            taskContext.execute(new Runnable() {
                public void run() {
                    specify(set.getAll(), should.containExactly());
                }
            });
        }
    }

    public class WhenARecoverableSetContainsSomeObjects {

        private String key1;
        private String key2;

        public void create() {
            taskContext.execute(new Runnable() {
                public void run() {
                    key1 = set.add(value1);
                    key2 = set.add(value2);
                }
            });
        }

        public void itContainsThoseObjects() {
            taskContext.execute(new Runnable() {
                public void run() {
                    specify(set.getAll(), should.containExactly(value1, value2));
                }
            });
        }

        public void afterRestartANewSetWithTheSamePrefixStillContainsThoseObjects() {
            taskContext.execute(new Runnable() {
                public void run() {
                    set = new RecoverableSet<StoredValue>(PREFIX, bindings, entities);
                    specify(set.getAll(), should.containExactly(value1, value2));
                }
            });
        }

        public void theObjectsCanBeRetrievedUsingTheirKeys() {
            taskContext.execute(new Runnable() {
                public void run() {
                    specify(set.get(key1), should.equal(value1));
                    specify(set.get(key2), should.equal(value2));
                }
            });
        }

        public void afterRemovingAnObjectTheSetDoesNotContainIt() {
            taskContext.execute(new Runnable() {
                public void run() {
                    specify(set.remove(key1), should.equal(value1));
                    specify(set.get(key1), should.equal(null));
                    specify(set.getAll(), should.containExactly(value2));
                }
            });
        }

        public void duplicateAddsAreIgnored() {
            taskContext.execute(new Runnable() {
                public void run() {
                    set.add(value1);
                    specify(set.getAll(), should.containExactly(value1, value2));
                }
            });
        }

        public void tryingToAccessObjectsUnderADifferentPrefixIsNotAllwed() {
            final String invalidKey = "otherPrefix" + RecoverableSet.SEPARATOR + "1";
            specify(new Block() {
                public void run() throws Throwable {
                    set.get(invalidKey);
                }
            }, should.raise(IllegalArgumentException.class));
            specify(new Block() {
                public void run() throws Throwable {
                    set.remove(invalidKey);
                }
            }, should.raise(IllegalArgumentException.class));
        }
    }


    private static class StoredValue implements EntityObject, Serializable {

        private final String value;

        public StoredValue(String value) {
            this.value = value;
        }

        public boolean equals(Object obj) {
            if (obj instanceof StoredValue) {
                StoredValue other = (StoredValue) obj;
                return value.equals(other.value);
            }
            return false;
        }
    }
}