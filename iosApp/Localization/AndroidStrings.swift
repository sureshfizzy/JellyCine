import Foundation

nonisolated enum StringModule: String {
    case shared
    case data
}

nonisolated enum AndroidStrings {

    static func get(_ key: String, _ argument: String? = nil, module: StringModule = .shared) -> String {
        guard let raw = catalog.value(for: key, module: module) else { return key }
        guard let argument else { return raw }
        return String(format: raw, argument)
    }

    private static let catalog = Catalog()
}

private nonisolated final class Catalog {

    private let folders: [String]
    private var cache: [String: [String: String]] = [:]
    private let lock = NSLock()

    init() {
        var result: [String] = []
        for identifier in Locale.preferredLanguages {
            let locale = Locale(identifier: identifier)
            guard let language = locale.language.languageCode?.identifier else { continue }
            if let region = locale.region?.identifier {
                result.append("values-\(language)-r\(region)")
            }
            result.append("values-\(language)")
        }
        result.append("values")
        folders = result
    }

    func value(for key: String, module: StringModule) -> String? {
        for folder in folders {
            if let value = strings(module: module, folder: folder)[key] {
                return value
            }
        }
        return nil
    }

    private func strings(module: StringModule, folder: String) -> [String: String] {
        let path = "AndroidStrings/\(module.rawValue)/\(folder)"
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache[path] { return cached }

        var parsed: [String: String] = [:]
        if let url = Bundle.main.url(forResource: "strings", withExtension: "xml", subdirectory: path),
           let parser = XMLParser(contentsOf: url) {
            let delegate = StringsParser()
            parser.delegate = delegate
            parser.parse()
            parsed = delegate.strings
        }
        cache[path] = parsed
        return parsed
    }
}

private nonisolated final class StringsParser: NSObject, XMLParserDelegate {

    var strings: [String: String] = [:]

    private var currentName: String?
    private var buffer = ""
    private var pluralsDepth = 0

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        switch elementName {
        case "plurals", "string-array":
            pluralsDepth += 1
        case "string" where pluralsDepth == 0:
            currentName = attributeDict["name"]
            buffer = ""
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if currentName != nil { buffer += string }
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        if currentName != nil, let text = String(data: CDATABlock, encoding: .utf8) { buffer += text }
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        switch elementName {
        case "plurals", "string-array":
            pluralsDepth -= 1
        case "string":
            if let name = currentName {
                strings[name] = Self.format(Self.unescape(buffer))
            }
            currentName = nil
        default:
            break
        }
    }

    private static func unescape(_ text: String) -> String {
        var result = ""
        var iterator = text.makeIterator()
        while let char = iterator.next() {
            guard char == "\\" else {
                result.append(char)
                continue
            }
            guard let next = iterator.next() else { break }
            switch next {
            case "n": result.append("\n")
            case "t": result.append("\t")
            default: result.append(next)
            }
        }
        return result
    }

    private static func format(_ text: String) -> String {
        text
            .replacingOccurrences(of: #"%(\d+)\$s"#, with: "%$1$@", options: .regularExpression)
            .replacingOccurrences(of: #"%s"#, with: "%@", options: .regularExpression)
    }
}
