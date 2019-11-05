object TSContactService {

  import java.util.UUID

  trait ContactsService {
    def createContacts(contacts: Nothing, headers: Nothing): Nothing

    def create(addContacts: Nothing, headers: Nothing): Nothing

    def delete(contactsId: UUID, headers: Nothing): Nothing

    def modify(contacts: Nothing, headers: Nothing): Nothing

    def getAllContacts(headers: Nothing): Nothing

    def findContactsById(id: UUID, headers: Nothing): Nothing

    def findContactsByAccountId(accountId: UUID, headers: Nothing): Nothing
  }

}
