package org.crygier.graphql

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

//@Configuration
@SpringBootTest(classes = TestApplication)
class EmbeddedQueryExecutorTest extends Specification {

    @Autowired
    private GraphQLExecutor executor

    def 'Gets all test entities'() {
        given:
        def query = '''
        {
          TestEntity {
            id {
				firstPart
				secondPart
			}
            someValue
          }
        }
        '''
        def expected = [
			TestEntity: [
				[id: [firstPart: 1, secondPart: 2], someValue: 'one then two'],
				[id: [firstPart: 1, secondPart: 1], someValue: 'one then one'],
				[id: [firstPart: 2, secondPart: 1], someValue: 'two then one']
			]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }
}
