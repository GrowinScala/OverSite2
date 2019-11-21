# Oversite2

Oversite2 is a remake of [Oversite](https://github.com/GrowinScala/OverSite). 
Like its predecessor, it's an open-source email service written in Scala
that allows users to grant other users supervision access of specified chats.

The supervising user will have access to all the emails that the supervised
user has sent or received within the specified chat. 

Current version: 0.1

## Requirements

* A MySQL database with an empty schema called 'oversitedb'
* An HTTP API Client ([Postman](https://www.getpostman.com/) is recommended)

## Running

* Clone this repository
`git clone https://github.com/GrowinScala/OverSite2.git`

* Create a `.conf` file that will override the application.conf file. 
This file should specify the user and password associated with the
database along with the key to be used for encryption and the filesystem 
location of the flyway migrations. Example:
```
include "application.conf"

dbinfo = {
  properties = {
    user = "user"
    password = "password"
  }
}

secretKey = "secretKey"

migrationLocation = "C:/Users/User/OverSite2/app/repositories/db/migration"
```  

* In the project directory start sbt and run the applicattion specifying 
the location of the overriding `.conf` file.

* Example: `sbt run -Dconfig.file=C:\User\Files\override.conf`

Go to <http://localhost:9000> to see the running web application.

  <br/>

---

### License ###

Open source licensed under the [MIT License](https://opensource.org/licenses/MIT)
