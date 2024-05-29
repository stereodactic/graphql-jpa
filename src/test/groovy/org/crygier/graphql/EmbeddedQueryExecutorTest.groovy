
package org.crygier.graphql

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

//@Configuration
@SpringBootTest(classes = TestApplication)
class EmbeddedQueryExecutorTest extends Specification {

    @Autowired
    private GraphQLExecutor executor;

	//This currently is not going to work because the jpa data fetcher doesn't know how to properly fetch it
	/*
    def 'Query embedded object'() {
		given:
        def query = '''
        {
          EmbeddingTest {
            character {
			  name
			}
			episode
			age
			weight
          }
        }
        '''
        def expected = [
                EmbeddingTest: [
                        [ character: [name: 'Luke Skywalker'], episode: 'A_NEW_HOPE', age: 19, weight: 150 ],
                ]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
		
	}
	*/
	
}
