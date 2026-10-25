package fyi.kittens.shared.ui

interface Navigator {
    fun navigateToProfile(did: String)
    fun navigateToPost(uri: String)
    fun navigateToImage(url: String, alt: String?)
    fun navigateToUserList(identifier: String, type: Any) // type can be casted back in app/wear
}
