// Runs Node as a plain executable. nodejs-mobile ships Node as libnode.so for embedding;
// JS services run it in their own processes instead (see JsServices.kt), so this is Node's
// main(). Packaged as liblunacynode.so so Android installs it next to libnode.so.
namespace node { int Start(int argc, char** argv); }
int main(int argc, char** argv) { return node::Start(argc, argv); }
