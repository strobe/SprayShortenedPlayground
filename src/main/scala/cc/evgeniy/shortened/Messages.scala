package cc.evgeniy.shortened

import cc.evgeniy.shortened.RequestParams.{ClickParameter, SourceLinkParameter}

case class AskTokenResponse(user_id: Int)
case class AskPostLinkResponse(link: SourceLinkParameter)
case class AskGetLinksResponse(token: String, offset: Int, limit: Int)
case class AskGetLinkResponse(token: String, code: String)
case class AskGetClicksResponse(token: String, code: String, offset: Int, limit: Int)
case class AskGetFolderResponse(token: String)
case class AskGetFolderResponseById(token: String, id: Int, offset: Int, limit: Int)
case class AskAddNewClicks(code: String, click: ClickParameter)