package biweekly.io.scribe.property;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import biweekly.ICalVersion;
import biweekly.io.scribe.property.Sensei.Check;
import biweekly.property.Status;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * @author Michael Angstadt
 */
public class StatusScribeTest {
	private final StatusScribe scribe = new StatusScribe();
	private final Sensei<Status> sensei = new Sensei<Status>(scribe);

	@Test
	public void writeText_needsAction() {
		Status status = Status.needsAction();

		sensei.assertWriteText(status).version(ICalVersion.V1_0).run("NEEDS ACTION");
		sensei.assertWriteText(status).version(ICalVersion.V2_0_DEPRECATED).run("NEEDS-ACTION");
		sensei.assertWriteText(status).version(ICalVersion.V2_0).run("NEEDS-ACTION");
	}

	@Test
	public void parseText_needsAction() {
		sensei.assertParseText("NEEDS ACTION").versions(ICalVersion.V1_0).run(isNeedsAction(true));
		sensei.assertParseText("NEEDS ACTION").versions(ICalVersion.V2_0_DEPRECATED, ICalVersion.V2_0).run(isNeedsAction(false));

		sensei.assertParseText("NEEDS-ACTION").versions(ICalVersion.V1_0).run(isNeedsAction(true));
		sensei.assertParseText("NEEDS-ACTION").versions(ICalVersion.V2_0_DEPRECATED, ICalVersion.V2_0).run(isNeedsAction(true));
	}

	private Check<Status> isNeedsAction(final boolean isNeedsAction) {
		return new Check<Status>() {
			public void check(Status actual) {
				assertEquals(isNeedsAction, actual.isNeedsAction());
			}
		};
	}
}