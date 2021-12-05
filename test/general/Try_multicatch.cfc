component extends="org.lucee.cfml.test.LuceeTestCase"{
	function beforeAll(){}

	function afterAll(){}

	function run( testResults , testBox ) {
		describe( "Multicatch try/catch", function() {
			it(title="catches a foo or bar", body=function(){
				try {
					var caughtType = 0;
					try {
						throw(type="foo");
					}
					catch (foo | bar | baz e) {
						caughtType = e.type;
					}
					expect(caughtType).toBe("foo");

					var caughtType = 0;
					try {
						throw(type="bar");
					}
					catch (foo | bar | baz e) {
						caughtType = e.type;
					}
					expect(caughtType).toBe("bar");
				}
				catch (any e) {
					expect(false).toBe(true, "Unhandled exception during test.");
				}
			});
		});
	}
}
