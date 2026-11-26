package com.example.textedit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Bảng ngôn ngữ: tên, phần mở rộng, từ khóa và quy tắc chú thích/chuỗi. */
final class Langs {

    private static final HashMap<String, Lang> BY_EXT = new HashMap<>();
    private static final HashMap<String, Lang> BY_FILE = new HashMap<>();
    private static final HashMap<String, Lang> BY_NAME = new HashMap<>();
    private static final ArrayList<Lang> ALL = new ArrayList<>();

    /** Ngôn ngữ phụ dùng cho phần giá trị trong YAML/INI. */
    static final Lang VALUE_YAML = new Lang("yaml-value", Lang.GENERIC)
            .words("true false null yes no on off True False Null ~").line("#");
    static final Lang VALUE_INI = new Lang("ini-value", Lang.GENERIC)
            .words("true false null yes no on off True False");

    // ---------- từ khóa ----------
    private static final String KW_C = "auto break case char const continue default do double else enum extern float for goto if inline int long register restrict return short signed sizeof static struct switch typedef union unsigned void volatile while _Bool NULL true false";
    private static final String KW_CPP = KW_C + " alignas alignof and and_eq asm bitand bitor bool catch class compl concept constexpr const_cast decltype delete dynamic_cast explicit export friend mutable namespace new noexcept not not_eq nullptr operator or or_eq private protected public reinterpret_cast requires static_assert static_cast template this thread_local throw try typeid typename using virtual wchar_t xor xor_eq override final";
    private static final String KW_CS = "abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while var async await record init get set yield partial where when with";
    private static final String KW_JAVA = "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record sealed permits yield true false null";
    private static final String KW_KOTLIN = "as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set value where abstract actual annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg";
    private static final String KW_SCALA = "abstract case catch class def do else extends false final finally for forSome if implicit import lazy match new null object override package private protected return sealed super this throw trait try true type val var while with yield given using enum then export";
    private static final String KW_GROOVY = "abstract as assert boolean break byte case catch char class const continue def default do double else enum extends false final finally float for goto if implements import in instanceof int interface long native new null package private protected public return short static strictfp super switch synchronized this threadsafe throw throws trait transient true try void volatile while";
    private static final String KW_SWIFT = "associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private precedencegroup protocol public rethrows static struct subscript typealias var break case catch continue default defer do else fallthrough for guard if in repeat return throw switch where while Any as await false is nil self Self super throws true try async actor some any";
    private static final String KW_GO = "break default func interface select case defer go map struct chan else goto package switch const fallthrough if range type continue for import return var true false nil iota";
    private static final String KW_RUST = "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while macro_rules union";
    private static final String KW_DART = "abstract as assert async await break case catch class const continue covariant default deferred do dynamic else enum export extends extension external factory false final finally for Function get hide if implements import in interface is late library mixin new null on operator part required rethrow return set show static super switch sync this throw true try typedef var void while with yield";
    private static final String KW_JS = "async await break case catch class const continue debugger default delete do else export extends false finally for from function get if import in instanceof let new null of return set static super switch this throw true try typeof undefined var void while with yield as";
    private static final String KW_TS = KW_JS + " abstract any asserts bigint boolean constructor declare enum implements infer interface is keyof module namespace never number object override private protected public readonly require string symbol type unique unknown satisfies";
    private static final String KW_PHP = "abstract and array as break callable case catch class clone const continue declare default die do echo else elseif empty enddeclare endfor endforeach endif endswitch endwhile eval exit extends final finally fn for foreach function global goto if implements include include_once instanceof insteadof interface isset list match namespace new or print private protected public readonly require require_once return static switch throw trait try unset use var while xor yield true false null self parent enum";
    private static final String KW_PY = "False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case";
    private static final String KW_RUBY = "BEGIN END alias and begin break case class def defined? do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield require require_relative include extend attr_accessor attr_reader attr_writer puts print lambda proc";
    private static final String KW_PERL = "if else elsif unless while until for foreach do last next redo goto return sub my our local use no package require BEGIN END and or not xor eq ne lt gt le ge cmp x print say defined undef ref bless die warn shift push pop splice keys values each exists delete scalar wantarray";
    private static final String KW_SH = "if then else elif fi case esac for select while until do done in function time coproc echo exit return export local readonly declare typeset unset set shift source alias cd test true false eval exec trap break continue read printf";
    private static final String KW_PS = "begin break catch class continue data do dynamicparam else elseif end enum exit filter finally for foreach from function if in param process return switch throw trap try until using var while workflow true false null";
    private static final String KW_LUA = "and break do else elseif end false for function goto if in local nil not or repeat return then true until while";
    private static final String KW_R = "if else repeat while function for next break TRUE FALSE NULL Inf NaN NA NA_integer_ NA_real_ NA_character_ in library require";
    private static final String KW_JULIA = "baremodule begin break catch const continue do else elseif end export false finally for function global if import let local macro module quote return struct true try using while mutable abstract type primitive where in isa";
    private static final String KW_HS = "case class data default deriving do else foreign if import in infix infixl infixr instance let module newtype of then type where forall qualified as hiding True False";
    private static final String KW_ELIXIR = "after alias and case catch cond def defp defmodule defmacro defstruct defprotocol defimpl do else end false fn for if import in nil not or quote raise receive require rescue true try unless unquote use when with";
    private static final String KW_ERLANG = "after and andalso band begin bnot bor bsl bsr bxor case catch cond div end fun if let not of or orelse receive rem try when xor";
    private static final String KW_CLJ = "def defn defmacro defmulti defmethod defprotocol defrecord deftype do fn if if-let if-not let loop recur quote var throw try catch finally ns require use import in-ns when when-let when-not cond case for doseq nil true false";
    private static final String KW_LISP = "defun defvar defparameter defmacro defstruct defclass defgeneric defmethod let let* lambda if cond case when unless do dolist dotimes loop progn setq setf quote function t nil and or not";
    private static final String KW_SCHEME = "define lambda let let* letrec if cond case else when unless do begin set! quote quasiquote unquote and or not define-syntax syntax-rules require provide module";
    private static final String KW_SQL = "select from where insert into values update set delete create alter drop table index view database schema primary key foreign references unique not null default check constraint and or in is like between exists case when then else end join inner left right full outer cross on group by order having limit offset union all distinct as asc desc with begin commit rollback transaction truncate grant revoke varchar int integer bigint smallint decimal numeric float real double char text date time timestamp boolean true false returning using";
    private static final String KW_PASCAL = "and array as asm begin case class const constructor destructor div do downto else end except exports false file finalization finally for function goto if implementation in inherited initialization inline interface is label library mod nil not object of on or out packed private procedure program property protected public raise record repeat set shl shr string then threadvar to true try type unit until uses var while with xor";
    private static final String KW_VB = "AddHandler AddressOf And AndAlso As Boolean ByRef Byte ByVal Call Case Catch Char Class Const Continue Date Decimal Declare Default Delegate Dim Do Double Each Else ElseIf End Enum Error Event Exit False Finally For Friend Function Get Global GoTo Handles If Implements Imports In Inherits Integer Interface Is Let Lib Like Long Loop Me Mod Module MustInherit MustOverride MyBase MyClass Namespace New Next Not Nothing NotInheritable NotOverridable Object Of On Operator Option Optional Or OrElse Overloads Overridable Overrides ParamArray Partial Private Property Protected Public RaiseEvent ReadOnly ReDim REM RemoveHandler Resume Return Select Set Shadows Shared Short Single Static Step Stop String Structure Sub SyncLock Then Throw To True Try TypeOf Variant Wend When While With WriteOnly Xor";
    private static final String KW_BAT = "if else for in do goto call exit set echo rem pause setlocal endlocal shift not exist defined errorlevel equ neq lss leq gtr geq off on start title cd md mkdir rd del copy move type cls";
    private static final String KW_FORTRAN = "program end subroutine function module use implicit none integer real double precision complex logical character dimension parameter allocatable allocate deallocate if then else elseif endif do while enddo call return stop contains intent in out inout select case where forall write read print open close";
    private static final String KW_OCAML = "and as assert begin class constraint do done downto else end exception external false for fun function functor if in include inherit initializer lazy let match method module mutable new nonrec object of open or private rec sig struct then to true try type val virtual when while with";
    private static final String KW_FSHARP = KW_OCAML + " abstract base default delegate elif extern finally fixed global inline interface internal namespace null override select static upcast use yield member";
    private static final String KW_MATLAB = "break case catch classdef continue else elseif end for function global if otherwise parfor persistent return spmd switch try while";
    private static final String KW_TCL = "proc set if else elseif while for foreach switch break continue return expr puts namespace variable global upvar uplevel catch error eval list lappend lindex llength string array regexp regsub source package require";
    private static final String KW_AWK = "BEGIN END if else while for do break continue next exit function return print printf getline delete in NR NF FS OFS RS ORS FILENAME";
    private static final String KW_PROLOG = "is mod rem true fail not dynamic discontiguous module use_module";
    private static final String KW_ASM = "mov push pop add sub mul div inc dec cmp jmp je jne jz jnz jg jl jge jle call ret int lea and or xor not shl shr nop test loop section global extern db dw dd dq resb resw resd equ times eax ebx ecx edx esi edi esp ebp rax rbx rcx rdx rsi rdi rsp rbp r8 r9 r10 r11 r12 r13 r14 r15";
    private static final String KW_ZIG = "addrspace align allowzero and anyframe anytype asm async await break callconv catch comptime const continue defer else enum errdefer error export extern fn for if inline linksection noalias noinline nosuspend opaque or orelse packed pub resume return struct suspend switch test threadlocal try union unreachable usingnamespace var volatile while true false null undefined";
    private static final String KW_NIM = "addr and as asm bind block break case cast concept const continue converter defer discard distinct div do elif else end enum except export finally for from func if import in include interface is isnot iterator let macro method mixin mod nil not notin object of or out proc ptr raise ref return shl shr static template try tuple type using var when while xor yield";
    private static final String KW_CRYSTAL = "abstract alias annotation as asm begin break case class def do else elsif end ensure enum extend for fun if in include lib macro module next nil of out pointerof private protected require rescue return select self sizeof struct super then type typeof union uninitialized unless until when while with yield true false";
    private static final String KW_D = "abstract alias align asm assert auto body bool break byte case cast catch char class const continue default delegate delete deprecated do double else enum export extern false final finally float for foreach function goto if immutable import in inout int interface invariant is lazy long mixin module new nothrow null out override package pragma private protected public pure real ref return scope shared short static struct super switch synchronized template this throw true try typeid typeof ubyte uint ulong union unittest ushort version void while with";
    private static final String KW_SOL = "pragma contract library interface function modifier event struct enum mapping address uint int bool string bytes public private internal external view pure payable returns return if else for while do break continue emit require assert revert import is new delete memory storage calldata constructor true false this";
    private static final String KW_VERILOG = "module endmodule input output inout wire reg logic always always_ff always_comb assign begin end if else case endcase for while parameter localparam integer initial posedge negedge function endfunction task endtask generate endgenerate class endclass";
    private static final String KW_VHDL = "library use entity architecture is of begin end signal variable constant process port map generic if then elsif else case when for loop while in out inout std_logic std_logic_vector integer boolean type subtype component function procedure return and or not xor";
    private static final String KW_GLSL = "attribute const uniform varying layout centroid flat smooth noperspective patch sample break continue do for while switch case default if else in out inout float int void bool true false discard return mat2 mat3 mat4 vec2 vec3 vec4 ivec2 ivec3 ivec4 sampler2D samplerCube struct precision highp mediump lowp";
    private static final String KW_PROTO = "syntax package import option message enum service rpc returns stream oneof map repeated optional required reserved extensions extend true false string int32 int64 uint32 uint64 sint32 sint64 fixed32 fixed64 sfixed32 sfixed64 bool bytes float double";
    private static final String KW_GQL = "query mutation subscription fragment on type interface union enum scalar input extend schema directive implements true false null";
    private static final String KW_HCL = "resource data variable output locals module provider terraform for in if true false null";
    private static final String KW_DOCKER = "FROM RUN CMD LABEL MAINTAINER EXPOSE ENV ADD COPY ENTRYPOINT VOLUME USER WORKDIR ARG ONBUILD STOPSIGNAL HEALTHCHECK SHELL AS";
    private static final String KW_MAKE = "ifeq ifneq ifdef ifndef else endif include define endef export override unexport vpath .PHONY";
    private static final String KW_CMAKE = "cmake_minimum_required project add_executable add_library target_link_libraries include_directories set if else elseif endif foreach endforeach while endwhile function endfunction macro endmacro find_package install option message list string file";
    private static final String KW_NGINX = "server location listen server_name root index return rewrite proxy_pass upstream http events error_log access_log include ssl_certificate fastcgi_pass try_files";
    private static final String KW_VIM = "if else elseif endif for endfor while endwhile function endfunction let set call return execute autocmd augroup syntax highlight map nmap imap nnoremap inoremap";
    private static final String KW_COFFEE = "and by catch class else extends false finally for if in is isnt loop new no not null of off on or return super switch then this throw true try typeof undefined unless until when while yes";
    private static final String KW_ELM = "module import exposing type alias port if then else case of let in where as";
    private static final String KW_ADA = "abort abs abstract accept access aliased all and array at begin body case constant declare delay delta digits do else elsif end entry exception exit for function generic goto if in interface is limited loop mod new not null of or others out overriding package pragma private procedure protected raise range record rem renames requeue return reverse select separate some subtype synchronized tagged task terminate then type until use when while with xor";
    private static final String KW_APPLESCRIPT = "tell end if then else repeat with set to get return on error try display dialog application";
    private static final String KW_AHK = "if else return loop while for break continue global local static";
    private static final String KW_JSON = "true false null";
    private static final String KW_LATEX = "";
    private static final String KW_OBJC = KW_C + " id self super nil YES NO BOOL SEL IMP Class";
    private static final String KW_VALA = "abstract as base bool break case catch class const construct continue default delegate delete do else enum errordomain extern false finally for foreach get if in inline int interface internal is lock namespace new null out override owned private protected public ref return set signal sizeof static string struct switch this throw true try typeof uint unowned using var virtual void weak while yield";
    private static final String KW_HAXE = "abstract break case cast catch class continue default do dynamic else enum extends extern false final for function if implements import in inline interface macro new null override package private public return static super switch this throw true try typedef untyped using var while";
    private static final String KW_SMALLTALK = "self super true false nil thisContext";
    private static final String KW_COBOL = "identification division program-id environment data procedure section working-storage move to add subtract multiply divide giving perform until varying if else end-if display accept stop run pic picture value call using returning";
    private static final String KW_ODIN = "package import proc struct enum union defer return if else for in when switch case break continue foreign true false nil using";
    private static final String KW_PONY = "actor be class compiler_intrinsic consume continue do else elseif embed end error for fun if ifdef in interface is isolated iso lambda let match new object primitive recover ref repeat return struct then this trait trn try type until use var where while with true false";

    static {
        // ===== Họ C: // và /* */ =====
        c("C", "c h", KW_C).preproc();
        c("C++", "cpp cc cxx c++ hpp hh hxx h++ ipp tpp inl", KW_CPP).preproc().types();
        c("Objective-C", "m mm", KW_OBJC).preproc().types();
        c("C#", "cs csx", KW_CS).preproc().types();
        c("Java", "java", KW_JAVA).types();
        c("Kotlin", "kt kts", KW_KOTLIN).types();
        c("Scala", "scala sc sbt", KW_SCALA).types();
        c("Groovy", "groovy gvy gradle jenkinsfile", KW_GROOVY).types().triple();
        c("Swift", "swift", KW_SWIFT).types();
        c("Go", "go", KW_GO).quotes("\"`'");
        c("Rust", "rs", KW_RUST).quotes("\"").charLit().types();
        c("Dart", "dart", KW_DART).types();
        c("JavaScript", "js mjs cjs jsx es6 jsm", KW_JS).quotes("\"'`");
        c("TypeScript", "ts tsx mts cts", KW_TS).quotes("\"'`").types();
        c("PHP", "php php3 php4 php5 phtml", KW_PHP).dollar().line("//", "#");
        c("Zig", "zig", KW_ZIG);
        c("D", "d di", KW_D).types();
        c("Haxe", "hx", KW_HAXE).types();
        c("Solidity", "sol", KW_SOL);
        c("Verilog", "v sv svh vh", KW_VERILOG).preproc();
        c("GLSL", "glsl vert frag geom comp", KW_GLSL).preproc();
        c("HLSL", "hlsl fx cginc", KW_GLSL + " float2 float3 float4 half cbuffer Texture2D SamplerState").preproc();
        c("Protocol Buffers", "proto", KW_PROTO);
        c("Thrift", "thrift", "namespace include typedef const enum struct exception service extends throws oneway void bool byte i16 i32 i64 double string binary list map set required optional");
        hash("GraphQL", "graphql gql graphqls", KW_GQL).quotes("\"");
        c("Apex", "apex", KW_JAVA + " trigger before after insert update delete undelete");
        c("Arduino", "ino pde", KW_CPP + " setup loop pinMode digitalWrite digitalRead analogRead analogWrite HIGH LOW INPUT OUTPUT").preproc();
        c("CUDA", "cu cuh", KW_CPP + " __global__ __device__ __host__ __shared__ __constant__").preproc();
        c("OpenCL", "cl", KW_C + " __kernel kernel __global global __local local __constant constant").preproc();
        c("Vala", "vala vapi", KW_VALA).types();
        c("Jsonnet", "jsonnet libsonnet", "local function if then else self super import importstr error assert true false null").line("//", "#");
        c("WGSL", "wgsl", "fn let var const struct return if else for while loop break continue switch case default true false vec2 vec3 vec4 f32 i32 u32 bool array");
        c("Odin", "odin", KW_ODIN);
        c("Pony", "pony", KW_PONY).types();
        c("Squirrel", "nut", "base break case catch class clone continue const default delete else enum extends for foreach function if in instanceof local null resume return switch this throw try typeof while yield constructor true false");
        c("Ceylon", "ceylon", "abstract alias assert assign break case catch class continue dynamic else extends finally for function given if import in interface is let module new object of out outer package return satisfies super switch then this throw try value void while");
        c("Gosu", "gs gsx gst", KW_JAVA + " function property uses");
        c("Fantom", "fan", "abstract as break case catch class const continue default do else enum false final finally for foreach if internal is isnot it mixin native new null override private protected public return static super switch this throw true try using virtual void volatile while");
        c("JSON with Comments", "jsonc json5", KW_JSON).quotes("\"").keyStr();
        c("Cython", "pyx pxd pxi", KW_PY + " cdef cpdef cimport ctypedef").line("#").triple();

        // ===== Họ # =====
        hash("Python", "py pyw pyi pyz", KW_PY).triple();
        hash("Ruby", "rb rbw rake gemspec ru podspec", KW_RUBY).atAttr().types();
        hash("Perl", "pl pm t pod", KW_PERL).dollar();
        hash("Shell", "sh bash zsh ksh fish csh tcsh bashrc zshrc profile", KW_SH).dollar().quotes("\"'`");
        hash("PowerShell", "ps1 psm1 psd1", KW_PS).dollar().block("<#", "#>");
        hash("R", "r", KW_R);
        hash("Julia", "jl", KW_JULIA).triple();
        hash("Nim", "nim nims nimble", KW_NIM);
        hash("Crystal", "cr", KW_CRYSTAL).atAttr().types();
        hash("Elixir", "ex exs", KW_ELIXIR).types();
        hash("Makefile", "mk mak make", KW_MAKE).dollar();
        hash("CMake", "cmake", KW_CMAKE).ci().dollar();
        hash("Dockerfile", "dockerfile", KW_DOCKER).ci();
        hash("Tcl", "tcl tk", KW_TCL);
        hash("Awk", "awk", KW_AWK);
        hash("Raku", "raku rakumod p6 pl6", KW_PERL).dollar();
        hash("GDScript", "gd", "and as assert await break class class_name const continue elif else enum export extends for func if in is match not onready or pass return setget signal static tool var while yield true false null self");
        hash("CoffeeScript", "coffee", KW_COFFEE).quotes("\"'`");
        hash("Nix", "nix", "let in if then else with rec inherit import assert true false null");
        hash("Terraform", "tf tfvars hcl", KW_HCL).line("#", "//").block("/*", "*/");
        hash("Puppet", "pp", "class define node include require if else elsif unless case default true false undef");
        hash("Starlark", "bzl bazel star", KW_PY);
        hash("Meson", "meson", "if else elif endif foreach endforeach true false and or not");
        hash("Just", "justfile", "set export alias import mod if else");
        hash("Ignore file", "gitignore gitattributes dockerignore npmignore", "");
        hash("Nginx", "nginx", KW_NGINX).dollar();
        hash("Apache", "htaccess", "RewriteEngine RewriteRule RewriteCond Options Require Order Allow Deny AuthType AuthName Header SetEnv ErrorDocument Redirect");
        hash("Ninja", "ninja", "rule build default pool include subninja");
        hash("Kconfig", "kconfig", "config menu endmenu menuconfig if endif depends on select default bool tristate string int hex help source");
        hash("Gradle Properties", "gradleprops", "");
        hash("Crontab", "crontab", "");

        // ===== Họ -- =====
        dash("SQL", "sql ddl dml pgsql plsql mysql tsql psql", KW_SQL).ci().block("/*", "*/");
        dash("Lua", "lua", KW_LUA).block("--[[", "]]").quotes("\"'");
        dash("Haskell", "hs lhs hsc", KW_HS).block("{-", "-}").quotes("\"").charLit().types();
        dash("Elm", "elm", KW_ELM).block("{-", "-}").types();
        dash("Ada", "adb ads ada gpr", KW_ADA).ci().quotes("\"");
        dash("VHDL", "vhd vhdl", KW_VHDL).ci().quotes("\"");
        dash("Agda", "agda", "module import open data record where let in with using renaming public").block("{-", "-}");
        dash("Idris", "idr", "module import data record where let in with using public export total partial").block("{-", "-}");
        dash("PureScript", "purs", KW_ELM + " data newtype class instance forall foreign").block("{-", "-}").types();
        dash("Cabal", "cabal", "name version synopsis library executable build-depends main-is hs-source-dirs default-language");
        dash("AppleScript", "applescript scpt", KW_APPLESCRIPT).block("(*", "*)").quotes("\"");

        // ===== Họ ; =====
        lisp("Common Lisp", "lisp lsp asd", KW_LISP).block("#|", "|#");
        lisp("Scheme", "scm ss sld", KW_SCHEME).block("#|", "|#");
        lisp("Racket", "rkt", KW_SCHEME + " define-values struct match").block("#|", "|#");
        lisp("Clojure", "clj cljs cljc edn", KW_CLJ);
        lisp("Emacs Lisp", "el", KW_LISP + " defcustom defconst provide require").block("#|", "|#");
        lisp("Fennel", "fnl", "fn let if when unless each for while do values local var set lambda λ match");
        lisp("Hy", "hy", "defn setv if cond when for import require fn let");
        lang(new Lang("Assembly", Lang.GENERIC).ci().words(KW_ASM).line(";", "#", "//").block("/*", "*/").quotes("\"'"), "asm s nasm masm");
        lisp("LLVM IR", "ll", "define declare ret br call load store alloca add sub mul icmp label to type global constant");
        lang(new Lang("WebAssembly Text", Lang.GENERIC).words("module func param result local get set i32 i64 f32 f64 const add sub mul call export import memory table global block loop if else end br br_if return drop").line(";;").block("(;", ";)").quotes("\""), "wat wast");
        lisp("AutoHotkey", "ahk", KW_AHK).ci().block("/*", "*/");

        // ===== Họ % =====
        percent("Erlang", "erl hrl", KW_ERLANG).types();
        percent("MATLAB", "matlab", KW_MATLAB).block("%{", "%}").quotes("\"");
        percent("LaTeX", "tex sty cls latex ltx", KW_LATEX).backslash().quotes("");
        percent("BibTeX", "bib", "").quotes("\"");
        percent("Prolog", "pro prolog", KW_PROLOG).block("/*", "*/").types();
        percent("PostScript", "ps eps", "def dup pop exch roll index copy moveto lineto stroke fill newpath closepath gsave grestore showpage findfont scalefont setfont show").quotes("");

        // ===== Họ BASIC / Pascal / Fortran / ML =====
        lang(new Lang("Visual Basic", Lang.GENERIC).ci().words(KW_VB).line("'", "REM ", "rem ").quotes("\""), "vb vbs vba bas frm");
        lang(new Lang("QBasic", Lang.GENERIC).ci().words("dim as integer string print input if then else end for to next do loop while wend sub function call select case rem goto gosub return cls").line("'", "REM ", "rem ").quotes("\""), "bi qb4 fb");
        lang(new Lang("Batch", Lang.GENERIC).ci().words(KW_BAT).line("::", "REM ", "rem ").quotes("\"").dollar(), "bat cmd btm");
        lang(new Lang("Pascal", Lang.GENERIC).ci().words(KW_PASCAL).line("//").block("(*", "*)").quotes("'"), "pas pp lpr dpr");
        lang(new Lang("Fortran", Lang.GENERIC).ci().words(KW_FORTRAN).line("!").quotes("\"'"), "f f90 f95 f03 f08 for ftn");
        lang(new Lang("COBOL", Lang.GENERIC).ci().words(KW_COBOL).line("*>").quotes("\"'"), "cob cbl cobol");
        lang(new Lang("OCaml", Lang.GENERIC).words(KW_OCAML).block("(*", "*)").quotes("\"").charLit().types(), "ml mli");
        lang(new Lang("F#", Lang.GENERIC).words(KW_FSHARP).line("//").block("(*", "*)").quotes("\"").charLit().types(), "fs fsi fsx");
        lang(new Lang("Standard ML", Lang.GENERIC).words("abstype and andalso as case datatype do else end eqtype exception fn fun functor handle if in include infix infixr let local nonfix of op open orelse raise rec sharing sig signature struct structure then type val where while with withtype").block("(*", "*)").quotes("\""), "sml sig");
        lang(new Lang("Reason", Lang.GENERIC).words(KW_OCAML + " switch pub").line("//").block("/*", "*/").quotes("\""), "re rei");
        lang(new Lang("Smalltalk", Lang.GENERIC).words(KW_SMALLTALK).block("\"", "\"").quotes("'"), "st");
        lang(new Lang("Vim Script", Lang.GENERIC).words(KW_VIM).line("\"").quotes("'"), "vim vimrc");
        lang(new Lang("Brainfuck", Lang.GENERIC).quotes(""), "bf");

        // ===== Markup =====
        markup("HTML", "html htm xhtml shtml jsp asp aspx cshtml razor vue svelte ejs hbs handlebars mustache twig njk jinja jinja2 j2 liquid erb tpl", false);
        markup("XML", "xml xsd xsl xslt svg xaml plist rss atom wsdl csproj vbproj fsproj props targets resx config manifest xul kml gpx dtd", true);

        // ===== CSS =====
        lang(new Lang("CSS", Lang.CSS).quotes("\"'"), "css");
        lang(new Lang("SCSS", Lang.CSS).line("//"), "scss sass");
        lang(new Lang("Less", Lang.CSS).line("//"), "less");
        lang(new Lang("Stylus", Lang.CSS).line("//"), "styl");

        // ===== Dữ liệu / văn bản có cấu trúc =====
        lang(new Lang("JSON", Lang.GENERIC).words(KW_JSON).quotes("\"").keyStr(), "json geojson webmanifest ipynb map");
        lang(new Lang("YAML", Lang.YAML), "yml yaml");
        lang(new Lang("TOML", Lang.INI), "toml");
        lang(new Lang("INI", Lang.INI), "ini cfg conf properties desktop editorconfig gitconfig env");
        lang(new Lang("Markdown", Lang.MARKDOWN), "md markdown mdown mkd mdx rmd");
        Lang adoc = new Lang("AsciiDoc", Lang.MARKDOWN);
        adoc.adoc = true;
        lang(adoc, "adoc asciidoc asc");
        lang(new Lang("Diff", Lang.DIFF), "diff patch");
        lang(new Lang("Log", Lang.LOG), "log");

        // ===== Tên file đặc biệt =====
        file("Makefile", "Makefile");
        file("Makefile", "makefile");
        file("Makefile", "GNUmakefile");
        file("Dockerfile", "Dockerfile");
        file("CMake", "CMakeLists.txt");
        file("Ruby", "Gemfile");
        file("Ruby", "Rakefile");
        file("Ruby", "Podfile");
        file("Ruby", "Vagrantfile");
        file("Groovy", "Jenkinsfile");
        file("Starlark", "BUILD");
        file("Starlark", "WORKSPACE");
        file("Shell", ".bashrc");
        file("Shell", ".zshrc");
        file("Shell", ".profile");
        file("Shell", ".bash_profile");
        file("Ignore file", ".gitignore");
        file("INI", ".env");
        file("INI", ".gitconfig");
        file("INI", ".editorconfig");
        file("Nginx", "nginx.conf");
        file("Apache", ".htaccess");
        file("Apache", "httpd.conf");
        file("Just", "justfile");
        file("JSON", ".eslintrc");
        file("JSON", ".babelrc");
        file("Python", "SConstruct");
        file("Python", "SConscript");
        file("Python", "requirements.txt");
    }

    private Langs() { }

    // ---------- đăng ký ----------

    private static Lang lang(Lang l, String exts) {
        ALL.add(l);
        BY_NAME.put(l.name.toLowerCase(Locale.ROOT), l);
        for (String e : exts.split("\\s+")) {
            if (!e.isEmpty()) BY_EXT.put(e.toLowerCase(Locale.ROOT), l);
        }
        return l;
    }

    private static void file(String langName, String fileName) {
        Lang l = BY_NAME.get(langName.toLowerCase(Locale.ROOT));
        if (l != null) BY_FILE.put(fileName.toLowerCase(Locale.ROOT), l);
    }

    private static Lang c(String name, String exts, String kw) {
        Lang l = new Lang(name, Lang.GENERIC).words(kw).line("//").block("/*", "*/");
        return lang(l, exts);
    }

    private static Lang hash(String name, String exts, String kw) {
        Lang l = new Lang(name, Lang.GENERIC).words(kw).line("#");
        return lang(l, exts);
    }

    private static Lang dash(String name, String exts, String kw) {
        Lang l = new Lang(name, Lang.GENERIC).words(kw).line("--");
        return lang(l, exts);
    }

    private static Lang lisp(String name, String exts, String kw) {
        Lang l = new Lang(name, Lang.GENERIC).words(kw).line(";").quotes("\"").dash();
        return lang(l, exts);
    }

    private static Lang percent(String name, String exts, String kw) {
        Lang l = new Lang(name, Lang.GENERIC).words(kw).line("%");
        return lang(l, exts);
    }

    private static void markup(String name, String exts, boolean xml) {
        Lang l = new Lang(name, Lang.HTML);
        l.xml = xml;
        lang(l, exts);
    }

    // ---------- tra cứu ----------

    /** Tìm ngôn ngữ theo tên file; trả về null nếu là văn bản thường. */
    static Lang forFile(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        Lang l = BY_FILE.get(lower);
        if (l != null) return l;
        if (lower.startsWith("dockerfile")) return BY_NAME.get("dockerfile");
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            return BY_EXT.get(lower.substring(dot + 1));
        }
        return null;
    }

    static Lang byName(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    static List<String> names() {
        ArrayList<String> out = new ArrayList<>();
        for (Lang l : ALL) out.add(l.name);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static int languageCount() {
        return ALL.size();
    }

    static int extensionCount() {
        return BY_EXT.size() + BY_FILE.size();
    }
}
