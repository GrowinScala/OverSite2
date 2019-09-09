package model.types

import repositories.dtos.ChatPreview

object testTypeAliases {
	type EmailPreview = Option[(String, ChatPreview)]
	
}
