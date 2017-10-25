package org.crygier.graphql

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@Configuration
@SpringBootTest(classes = TestApplication)
class CollectionTest extends Specification {

    @Autowired
    private GraphQLExecutor executor

	def 'retrieves a string element collection'() {
        given:
        def query = '''
        {
          CollectionEntity {
            id
            strings
          }
        }
        '''
        def expected = [
			CollectionEntity: [
			   [ id: '1', strings: ['uno', 'dos', 'tres'] ],
			   [ id: '2', strings: [] ],
			   [ id: '3', strings: ['odin', 'dva'] ]
			]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }

	def 'retrieves a object element collection'() {
        given:
        def query = '''
        {
          CollectionEntity {
            id
            objects {
			  color
			  shape
			}
          }
        }
        '''
        def expected = [
			CollectionEntity: [
			   [ id: '1', objects: [ [color: 'red', shape: 'square'], [color: 'blue', shape: 'circle'] ] ],
			   [ id: '2', objects: [ [color: 'yellow', shape: 'oval'] ] ],
			   [ id: '3', objects: [] ]
			]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }

	def 'retrieves a object element collection with nested entity'() {
        given:
        def query = '''
        {
          CollectionEntity {
            id
            objects {
			  typeEntity {
			    id
				name
				description
			  }
			}
          }
        }
        '''
        def expected = [
			CollectionEntity: [
			   [ id: '1', objects: [ [typeEntity: [id: '1', name: 'alpha', description: 'alpha type']] , [typeEntity: [id: '2', name: 'beta', description: 'beta type' ] ] ] ],
			   [ id: '2', objects: [ [typeEntity: [id: '3', name: 'gamma', description: 'gamma type']] ] ],
			   [ id: '3', objects: [] ]
			]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }
	
}
